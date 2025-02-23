package yuki.androidportforwarder.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import yuki.androidportforwarder.R
import yuki.androidportforwarder.data.model.ForwardRule
import yuki.androidportforwarder.data.repository.RuleRepository
import yuki.androidportforwarder.databinding.ActivityMainBinding
import yuki.androidportforwarder.service.PortForwardService
import yuki.androidportforwarder.ui.adapter.RuleAdapter

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: RuleRepository
    private val adapter = RuleAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = RuleRepository(this)

        setupUI()
        startService(Intent(this, PortForwardService::class.java))
    }

    private fun setupUI() {
        binding.apply {
            rulesRecyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
            rulesRecyclerView.adapter = adapter

            fabAdd.setOnClickListener {
                startActivity(Intent(this@MainActivity, RuleEditActivity::class.java))
            }
        }

        adapter.onItemClick = { rule ->
            Intent(this, RuleEditActivity::class.java).apply {
                putExtra("RULE", rule)
                startActivity(this)
            }
        }

        adapter.onSwitchChanged = { rule, isChecked ->
            repository.saveRules(adapter.currentList.map {
                if (it.ruleName == rule.ruleName) it.copy(isActive = isChecked) else it
            })
        }

        loadRules()
    }

    private fun loadRules() {
        adapter.submitList(repository.loadRules())
    }

    override fun onResume() {
        super.onResume()
        loadRules()
    }
}