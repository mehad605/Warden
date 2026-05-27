package com.warden.app.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.warden.app.databinding.ActivitySelectAppsBinding
import com.warden.app.databinding.SelectAppsItemBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.warden.app.utils.AppFilter

class SelectAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectAppsBinding
    private lateinit var selectedApps: HashSet<String>
    private var allApps: List<AppItem> = emptyList()
    private var filteredApps: List<AppItem> = emptyList()

    data class AppItem(
        val label: String,
        val packageName: String,
        val icon: Drawable
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra("TITLE") ?: "Select Apps"
        binding.toolbar.title = title

        val preSelected = intent.getStringArrayListExtra("PRE_SELECTED_APPS") ?: emptyList()
        selectedApps = HashSet(preSelected)

        binding.appList.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            loadApps()
            setupSearch()
            setupAdapter()
        }

        binding.btnConfirm.setOnClickListener {
            val resultIntent = Intent().apply {
                putStringArrayListExtra("SELECTED_APPS", ArrayList(selectedApps))
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private suspend fun loadApps() = withContext(Dispatchers.Default) {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val list = mutableListOf<AppItem>()

        for (app in installedApps) {
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null && app.packageName != packageName) {
                val label = app.loadLabel(pm).toString()
                val icon = app.loadIcon(pm)
                list.add(AppItem(label, app.packageName, icon))
            }
        }
        list.sortBy { it.label.lowercase(Locale.ROOT) }
        allApps = list
        filteredApps = list
    }


    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })
    }

    private fun filterList(query: String?) {
        if (query.isNullOrBlank()) {
            filteredApps = allApps
        } else {
            val lowerQuery = query.lowercase(Locale.ROOT)
            filteredApps = allApps.filter {
                it.label.lowercase(Locale.ROOT).contains(lowerQuery) || 
                it.packageName.lowercase(Locale.ROOT).contains(lowerQuery)
            }
        }
        (binding.appList.adapter as? AppAdapter)?.notifyDataSetChanged()
    }

    private fun setupAdapter() {
        binding.appList.adapter = AppAdapter()
    }

    inner class AppAdapter : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        inner class ViewHolder(val itemBinding: SelectAppsItemBinding) : RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = SelectAppsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = filteredApps[position]
            holder.itemBinding.tvAppName.text = app.label
            holder.itemBinding.ivAppIcon.setImageDrawable(app.icon)
            holder.itemBinding.cbSelect.isChecked = selectedApps.contains(app.packageName)

            holder.itemBinding.root.setOnClickListener {
                if (selectedApps.contains(app.packageName)) {
                    selectedApps.remove(app.packageName)
                } else {
                    selectedApps.add(app.packageName)
                }
                notifyItemChanged(position)
            }
        }

        override fun getItemCount(): Int = filteredApps.size
    }
}
