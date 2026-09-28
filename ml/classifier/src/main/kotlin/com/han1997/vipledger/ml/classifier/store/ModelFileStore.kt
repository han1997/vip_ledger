package com.han1997.vipledger.ml.classifier.store

import com.han1997.vipledger.ml.classifier.model.LinearModel
import com.han1997.vipledger.ml.classifier.model.ModelCodec
import com.han1997.vipledger.ml.classifier.model.ModelFormatException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 基于本地文件的 [ModelStore]（PR3 传入应用私有目录里的路径）。
 *
 * 写入严格走「**写同名临时文件 → `fd.sync()` → 同目录原子 move 覆盖**」：
 * 推理只读正式文件，因此训练中途崩溃/被打断时它读到的仍是上一份完整模型，绝不会读到半写内容
 * （PRD AC「权重文件替换是原子的」）。读侧把「不存在」与「损坏/版本不兼容」分开返回
 * （[ModelLoadResult]），任何失败都不抛到推理路径。
 */
class ModelFileStore(private val target: File) : ModelStore {

    /** 临时文件固定在正式文件同目录，保证 move 是同卷原子操作。 */
    private val tempFile: File get() = File(target.absoluteFile.parentFile, target.name + TEMP_SUFFIX)

    override fun load(): ModelLoadResult {
        if (!target.isFile) return ModelLoadResult.Missing
        val text = try {
            target.readText()
        } catch (e: IOException) {
            return ModelLoadResult.Invalid("read failed: ${e.message}")
        }
        return try {
            ModelLoadResult.Loaded(ModelCodec.decode(text))
        } catch (e: ModelFormatException) {
            ModelLoadResult.Invalid(e.message ?: "invalid model")
        }
    }

    override fun save(model: LinearModel) {
        // 先编码/校验：模型非法时在碰任何文件之前就失败，正式文件保持原样。
        val text = ModelCodec.encode(model)
        val parent = target.absoluteFile.parentFile
        parent?.mkdirs()

        val temp = tempFile
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
                stream.fd.sync()
            }
            moveAtomically(temp, target)
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    private fun moveAtomically(temp: File, destination: File) {
        val source = temp.toPath()
        val dest = destination.toPath()
        try {
            Files.move(
                source,
                dest,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            // 个别文件系统不支持 ATOMIC_MOVE 选项；退化为同目录 replace（仍是整文件替换，不是追加）。
            Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        /** 应用私有目录里的默认权重文件名（PR3 装配时使用）。 */
        const val DEFAULT_MODEL_FILE_NAME: String = "category_model.json"

        private const val TEMP_SUFFIX = ".tmp"
    }
}
