package com.touchforge.toolkit.activity

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.touchforge.toolkit.R
import com.touchforge.toolkit.core.schema.MappingSchema
import com.touchforge.toolkit.data.ProfileRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles VIEW intents for application/json mapping files (our .tfmap schema).
 * The user picks the target game; the file is validated before anything is saved.
 */
@AndroidEntryPoint
class ImportActivity : ComponentActivity() {

    @Inject lateinit var profiles: ProfileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (intent?.action != Intent.ACTION_VIEW || uri == null) {
            finish(); return
        }
        val text = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null) {
            toast(R.string.import_read_failed); finish(); return
        }
        when (val r = MappingSchema.load(text)) {
            is MappingSchema.LoadResult.Failed -> {
                toast(R.string.import_invalid); finish(); return
            }
            is MappingSchema.LoadResult.Ok -> chooseTargetAndSave(r.file.name.ifBlank { "imported" }, text)
        }
    }

    private fun chooseTargetAndSave(name: String, text: String) {
        val pm = packageManager
        val launchables = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_ALL,
        ).distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val labels = launchables.map { it.loadLabel(pm).toString() }
        val list = ListView(this)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        AlertDialog.Builder(this)
            .setTitle(R.string.import_pick_target)
            .setView(list)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .show()
        list.setOnItemClickListener { _, _, pos, _ ->
            val ri = launchables[pos]
            val pkg = ri.activityInfo.packageName
            val label = labels[pos]
            CoroutineScope(Dispatchers.Main.immediate).launch {
                val result = profiles.importMapping(text, pkg, label)
                val msg = if (result is ProfileRepository.SaveResult.Ok) R.string.import_ok else R.string.import_invalid
                toast(msg)
                finish()
            }
        }
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()
}
