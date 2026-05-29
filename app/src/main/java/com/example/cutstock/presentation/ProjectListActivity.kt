package com.example.cutstock.presentation

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cutstock.BuildConfig
import com.example.cutstock.CutStockApplication
import com.example.cutstock.R
import com.example.cutstock.databinding.ActivityProjectListBinding
import com.example.cutstock.domain.FreemiumPolicy
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ProjectListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectListBinding
    private val adapter = ProjectListAdapter(
        onOpen = { projectId -> viewModel.openProject(projectId) },
        onDelete = { projectId ->
            val item = (viewModel.uiState.value as? ProjectListUiState.Success)
                ?.projects
                ?.find { it.id == projectId }
            val name = item?.name ?: ""
            MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.delete_project_confirm, name))
                .setPositiveButton(R.string.delete) { _, _ ->
                    viewModel.deleteProject(projectId)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    )

    private val container by lazy { application as CutStockApplication }

    private val viewModel: ProjectListViewModel by viewModels {
        container.projectListViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.projectsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.projectsRecyclerView.adapter = adapter

        binding.addProjectFab.setOnClickListener {
            viewModel.createProject(getString(R.string.new_project))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { renderState(it) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { handleEvent(it) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isCreating.collect { binding.addProjectFab.isEnabled = !it }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_project_list, menu)
        if (BuildConfig.DEBUG) {
            menu.add(0, MENU_DEBUG_PRO, 0, R.string.debug_pro_toggle)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_help -> {
            showHelpDialog()
            true
        }
        R.id.menu_about -> {
            showAboutDialog()
            true
        }
        R.id.menu_upgrade -> {
            showUpgradeDialog()
            true
        }
        MENU_DEBUG_PRO -> {
            lifecycleScope.launch {
                val isPro = (viewModel.uiState.value as? ProjectListUiState.Success)?.isPro == true
                viewModel.toggleDebugPro(!isPro)
                Snackbar.make(binding.root, R.string.debug_pro_toggle, Snackbar.LENGTH_SHORT).show()
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun renderState(state: ProjectListUiState) {
        when (state) {
            ProjectListUiState.Loading -> {
                binding.progressBar.isVisible = true
            }

            is ProjectListUiState.Success -> {
                binding.progressBar.isVisible = false
                adapter.submitList(state.projects)
                binding.emptyTextView.isVisible = state.projects.isEmpty()

                if (!state.isPro) {
                    binding.freeTierHintTextView.isVisible = true
                    binding.freeTierHintTextView.text = getString(
                        R.string.free_tier_hint,
                        FreemiumPolicy.FREE_MAX_PROJECTS,
                        FreemiumPolicy.FREE_MAX_DEMAND_TYPES
                    )
                } else {
                    binding.freeTierHintTextView.isVisible = true
                    binding.freeTierHintTextView.text = getString(R.string.pro_badge)
                }
            }
        }
    }

    private fun handleEvent(event: ProjectListEvent) {
        when (event) {
            is ProjectListEvent.OpenProject -> {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_PROJECT_ID, event.projectId)
                )
            }

            is ProjectListEvent.ShowMessage ->
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()

            ProjectListEvent.ShowUpgrade -> showUpgradeDialog()
        }
    }

    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_help)
            .setMessage(R.string.help_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_about)
            .setMessage(R.string.about_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showUpgradeDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_upgrade, null, false)
        dialog.setContentView(view)
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.purchaseButton)
            .setOnClickListener {
                viewModel.purchasePro()
                dialog.dismiss()
            }
        dialog.show()
    }

    companion object {
        private const val MENU_DEBUG_PRO = 1001
    }
}
