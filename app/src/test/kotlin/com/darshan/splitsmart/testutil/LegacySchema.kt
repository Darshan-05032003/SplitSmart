package com.darshan.splitsmart.testutil

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File

/**
 * Builds a real database at an older schema version from the exported Room schema JSON
 * (`app/schemas/<db>/<version>.json`, the same files Room's AutoMigration reads at compile time), so a test can open it
 * with the current database class and exercise the real migration. [seed] runs raw SQL before the version is stamped.
 */
fun createLegacyDatabase(context: Context, name: String, version: Int, seed: (SQLiteDatabase) -> Unit = {}) {
    context.deleteDatabase(name)
    val schema = JSONObject(File("schemas/com.darshan.splitsmart.data.local.SplitSmartDatabase/$version.json").readText()).getJSONObject("database")
    SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { raw ->
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices")
            if (indices != null) for (j in 0 until indices.length()) raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) raw.execSQL(setup.getString(i).replace("\${IDENTITY_HASH}", schema.getString("identityHash")))
        seed(raw)
        raw.version = version
    }
}
