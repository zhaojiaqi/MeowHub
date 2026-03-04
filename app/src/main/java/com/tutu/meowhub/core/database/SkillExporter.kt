package com.tutu.meowhub.core.database

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object SkillExporter {

    private const val EXPORT_DIR = "shared_skills"
    private const val FILE_EXTENSION = ".meowskill.json"

    fun shareSkill(context: Context, entity: LocalSkillEntity, rawJson: String) {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val file = File(dir, "${entity.slug}$FILE_EXTENSION")
        file.writeText(rawJson)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, entity.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, "分享 Skill: ${entity.displayName}")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
