package top.yogiczy.mytv.ui.screens.leanback.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.ui.theme.LeanbackTheme

@Composable
fun LeanbackVideoPlayerErrorScreen(
    modifier: Modifier = Modifier,
    errorProvider: () -> String? = { null },
    retryStatusProvider: () -> String? = { null },
) {
    Box(modifier = modifier.fillMaxSize()) {
        val error = errorProvider()
        val retryStatus = retryStatusProvider()

        if (retryStatus != null) {
            // 重试中：显示倒计时提示
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        color = Color(0xCC000000),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color(0xFFfddd0e),
                    strokeWidth = 2.5.dp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "视频源连接失败",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = retryStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFfddd0e),
                    )
                }
            }
        } else if (error != null) {
            // 错误状态：显示错误提示
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        color = Color(0xFFfddd0e),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "视频源出错，请切换频道或更新直播源",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackVideoPlayerErrorScreenPreview() {
    LeanbackTheme {
        LeanbackVideoPlayerErrorScreen(
            errorProvider = { "ERROR_CODE_BEHIND_LIVE_WINDOW" }
        )
    }
}