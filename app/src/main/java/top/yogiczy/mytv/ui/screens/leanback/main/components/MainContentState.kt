package top.yogiczy.mytv.ui.screens.leanback.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvIdx
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvList
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.video.LeanbackVideoPlayerState
import top.yogiczy.mytv.ui.screens.leanback.video.rememberLeanbackVideoPlayerState
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.Loggable
import kotlin.math.max

@Stable
class LeanbackMainContentState(
    coroutineScope: CoroutineScope,
    private val videoPlayerState: LeanbackVideoPlayerState,
    private val iptvGroupList: IptvGroupList,
    private val onSourceSwitchRequired: ((currentSourceUrl: String) -> Unit)? = null,
) : Loggable() {
    private var _currentIptv by mutableStateOf(Iptv())
    val currentIptv get() = _currentIptv

    private var _currentIptvUrlIdx by mutableIntStateOf(0)
    val currentIptvUrlIdx get() = _currentIptvUrlIdx
    
    // 记录当前源URL已尝试失败的频道
    private val failedChannels = mutableSetOf<String>()
    // 记录当前频道URL切换次数
    private var currentChannelUrlAttempts = 0
    // 当前URL的重试次数
    private var retryCount = 0
    // 重试协程Job
    private var retryJob: Job? = null
    // 连续切频道失败次数
    private var consecutiveChannelFailures = 0
    // 重试状态描述（供UI显示）
    private var _retryStatus by mutableStateOf<String?>(null)
    val retryStatus get() = _retryStatus

    companion object {
        /** 单个URL最大重试次数 */
        private const val MAX_RETRY_COUNT = 3
        /** 重试间隔（毫秒） */
        private const val RETRY_INTERVAL_MS = 3000L
        /** 连续切频道失败超过此数则切源 */
        private const val MAX_CHANNEL_FAILURES_BEFORE_SOURCE_SWITCH = 8
    }

    private var _isPanelVisible by mutableStateOf(false)
    var isPanelVisible
        get() = _isPanelVisible
        set(value) {
            _isPanelVisible = value
        }

    private var _isSettingsVisible by mutableStateOf(false)
    var isSettingsVisible
        get() = _isSettingsVisible
        set(value) {
            _isSettingsVisible = value
        }

    private var _isTempPanelVisible by mutableStateOf(false)
    var isTempPanelVisible
        get() = _isTempPanelVisible
        set(value) {
            _isTempPanelVisible = value
        }

    private var _isQuickPanelVisible by mutableStateOf(false)
    var isQuickPanelVisible
        get() = _isQuickPanelVisible
        set(value) {
            _isQuickPanelVisible = value
        }

    init {
        changeCurrentIptv(iptvGroupList.iptvList.getOrElse(SP.iptvLastIptvIdx) {
            iptvGroupList.firstOrNull()?.iptvList?.firstOrNull() ?: Iptv()
        })

        videoPlayerState.onReady {
            coroutineScope.launch {
                val name = _currentIptv.name
                val urlIdx = _currentIptvUrlIdx
                delay(Constants.UI_TEMP_PANEL_SCREEN_SHOW_DURATION)
                if (name == _currentIptv.name && urlIdx == _currentIptvUrlIdx) {
                    _isTempPanelVisible = false
                }
            }

            // 记忆可播放的域名
            SP.iptvPlayableHostList += getUrlHost(_currentIptv.urlList[_currentIptvUrlIdx])

            // 成功播放，重置所有计数器
            retryCount = 0
            retryJob?.cancel()
            retryJob = null
            currentChannelUrlAttempts = 0
            consecutiveChannelFailures = 0
            _retryStatus = null
        }

        videoPlayerState.onError {
            // 取消上一个重试任务，避免重叠
            retryJob?.cancel()

            // 从记忆中删除不可播放的域名
            SP.iptvPlayableHostList -= getUrlHost(_currentIptv.urlList[_currentIptvUrlIdx])

            if (retryCount < MAX_RETRY_COUNT) {
                // 第一阶段：重试当前URL
                retryCount++
                val countdown = RETRY_INTERVAL_MS / 1000
                log.w("播放失败，${countdown}秒后重试（$retryCount/$MAX_RETRY_COUNT）：${_currentIptv.name}")
                _retryStatus = "正在重试 $retryCount/$MAX_RETRY_COUNT，${countdown}秒后重连..."
                val retryIptv = _currentIptv
                val retryUrlIdx = _currentIptvUrlIdx
                retryJob = coroutineScope.launch {
                    // 倒计时更新提示
                    for (remaining in countdown downTo 1) {
                        _retryStatus = "正在重试 $retryCount/$MAX_RETRY_COUNT，${remaining}秒后重连..."
                        delay(1000L)
                    }
                    // 确保仍在播放同一频道同一URL时才重试
                    if (_currentIptv == retryIptv && _currentIptvUrlIdx == retryUrlIdx) {
                        _retryStatus = "正在重试 $retryCount/$MAX_RETRY_COUNT..."
                        videoPlayerState.prepare(
                            url = retryIptv.urlList[retryUrlIdx],
                            userAgent = retryIptv.userAgent,
                            referer = retryIptv.referer
                        )
                    }
                }
            } else {
                // 重试次数耗尽，重置重试计数
                retryCount = 0
                _retryStatus = null
                currentChannelUrlAttempts++
                log.w("重试${MAX_RETRY_COUNT}次均失败（URL ${_currentIptvUrlIdx + 1}/${_currentIptv.urlList.size}）：${_currentIptv.name}")

                if (_currentIptvUrlIdx < _currentIptv.urlList.size - 1) {
                    // 第二阶段：还有其他URL，切换到下一个URL
                    log.i("切换到下一个URL：${_currentIptv.name}")
                    changeCurrentIptv(_currentIptv, _currentIptvUrlIdx + 1)
                } else {
                    // 第三阶段：当前频道所有URL都失败，切换下一个频道
                    currentChannelUrlAttempts = 0
                    consecutiveChannelFailures++
                    log.w("频道 ${_currentIptv.name} 所有URL均失败（连续失败频道数：$consecutiveChannelFailures/$MAX_CHANNEL_FAILURES_BEFORE_SOURCE_SWITCH）")
                    failedChannels.add(_currentIptv.channelName)

                    if (consecutiveChannelFailures >= MAX_CHANNEL_FAILURES_BEFORE_SOURCE_SWITCH) {
                        // 第四阶段：连续失败频道数超限，切换直播源
                        log.w("连续 $MAX_CHANNEL_FAILURES_BEFORE_SOURCE_SWITCH 个频道均无法播放，触发自动切换直播源")
                        checkAndSwitchSource(coroutineScope)
                    } else {
                        // 切换到下一个频道
                        log.i("自动切换到下一个频道")
                        changeCurrentIptv(getNextIptv())
                    }
                }
            }
        }

        videoPlayerState.onCutoff {
            // 直播流中断，重置重试计数后重新连接
            retryCount = 0
            _retryStatus = null
            changeCurrentIptv(_currentIptv, _currentIptvUrlIdx)
        }
    }

    private fun getPrevIptv(): Iptv {
        val currentIndex = iptvGroupList.iptvIdx(_currentIptv)
        return iptvGroupList.iptvList.getOrElse(currentIndex - 1) {
            iptvGroupList.lastOrNull()?.iptvList?.lastOrNull() ?: Iptv()
        }
    }

    private fun getNextIptv(): Iptv {
        val currentIndex = iptvGroupList.iptvIdx(_currentIptv)
        return iptvGroupList.iptvList.getOrElse(currentIndex + 1) {
            iptvGroupList.firstOrNull()?.iptvList?.firstOrNull() ?: Iptv()
        }
    }

    fun changeCurrentIptv(iptv: Iptv, urlIdx: Int? = null) {
        _isPanelVisible = false
        // 切换频道时取消当前重试任务，重置重试计数
        retryJob?.cancel()
        retryJob = null
        retryCount = 0
        _retryStatus = null

        if (iptv == _currentIptv && urlIdx == null) return

        if (iptv == _currentIptv && urlIdx != _currentIptvUrlIdx) {
            SP.iptvPlayableHostList -= getUrlHost(_currentIptv.urlList[_currentIptvUrlIdx])
        }

        _isTempPanelVisible = true

        _currentIptv = iptv
        SP.iptvLastIptvIdx = iptvGroupList.iptvIdx(_currentIptv)

        _currentIptvUrlIdx = if (urlIdx == null) {
            // 优先从记忆中选择可播放的域名
            max(0, _currentIptv.urlList.indexOfFirst {
                SP.iptvPlayableHostList.contains(getUrlHost(it))
            })
        } else {
            (urlIdx + _currentIptv.urlList.size) % _currentIptv.urlList.size
        }
        
        // 防御性检查：如果 urlList 为空，不播放
        if (iptv.urlList.isEmpty()) {
            log.w("频道 ${iptv.name} 的 URL 列表为空，无法播放")
            return
        }

        val url = iptv.urlList[_currentIptvUrlIdx]
        log.d("播放${iptv.name}（${_currentIptvUrlIdx + 1}/${_currentIptv.urlList.size}）: $url")
        
        // 传入频道的 User-Agent 和 Referer
        videoPlayerState.prepare(
            url = url,
            userAgent = iptv.userAgent,
            referer = iptv.referer
        )
    }

    fun changeCurrentIptvToPrev() {
        changeCurrentIptv(getPrevIptv())
    }

    fun changeCurrentIptvToNext() {
        changeCurrentIptv(getNextIptv())
    }
    
    /**
     * 检查是否需要切换源
     * 如果当前源的大部分频道都失败了，则触发源切换
     */
    private fun checkAndSwitchSource(coroutineScope: CoroutineScope) {
        val totalChannels = iptvGroupList.iptvList.size
        val failedCount = failedChannels.size
        val failureRate = if (totalChannels > 0) failedCount.toFloat() / totalChannels else 0f
        
        log.i("当前源失败统计：${failedCount}/${totalChannels}（${String.format("%.1f%%", failureRate * 100)}）")
        
        // 如果失败频道数>=5个，或失败率>=30%，则认为当前源不可用
        if (failedCount >= 5 || failureRate >= 0.3f) {
            log.w("当前源失败率过高，触发自动切换源机制")
            
            // 获取当前源URL
            val currentSourceUrl = SP.iptvSourceUrl
            
            // 触发源切换回调
            onSourceSwitchRequired?.invoke(currentSourceUrl)
        }
    }
    
    /**
     * 重置失败记录（切换源后调用）
     */
    fun resetFailureTracking() {
        failedChannels.clear()
        currentChannelUrlAttempts = 0
        retryCount = 0
        consecutiveChannelFailures = 0
        retryJob?.cancel()
        retryJob = null
        _retryStatus = null
        log.i("已重置失败跟踪记录")
    }
}

@Composable
fun rememberLeanbackMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: LeanbackVideoPlayerState = rememberLeanbackVideoPlayerState(),
    iptvGroupList: IptvGroupList = IptvGroupList(),
    onSourceSwitchRequired: ((currentSourceUrl: String) -> Unit)? = null,
) = remember {
    LeanbackMainContentState(
        coroutineScope = coroutineScope,
        videoPlayerState = videoPlayerState,
        iptvGroupList = iptvGroupList,
        onSourceSwitchRequired = onSourceSwitchRequired,
    )
}

private fun getUrlHost(url: String): String {
    return url.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: url
}