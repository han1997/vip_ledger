# P5 文字、语音、截图

Status: planned; not implemented.

- 四种输入统一编辑器/草稿确认；一句多笔可逐笔修改、批量确认，区分未识别字段。
- 语音按住/松开/取消/重试/转写编辑，离页停止，不存原始音频。系统端侧检测；联网转写独立开关默认关；无服务/权限/超时可改手工。
- 主动选图及系统分享、多图逐张 OCR，复用本机中文识别；金额/商户/时间/支付方式/订单号。冲突须补充；主动截图始终先确认。原图默认不存，可选附件。
- 技术参考：https://developer.android.com/reference/android/speech/SpeechRecognizer 、https://developer.android.com/training/data-storage/shared/photopicker 。系统识别可能联网，不能将普通服务冒称离线。

See docs/FEATURE-ROADMAP.md for shared contracts, acceptance and delivery requirements.
