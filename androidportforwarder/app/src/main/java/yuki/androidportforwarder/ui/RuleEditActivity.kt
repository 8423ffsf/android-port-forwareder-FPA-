package yuki.androidportforwarder.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import yuki.androidportforwarder.data.model.ForwardRule
import yuki.androidportforwarder.data.repository.RuleRepository
import yuki.androidportforwarder.databinding.ActivityRuleEditBinding

class RuleEditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRuleEditBinding
    private lateinit var repository: RuleRepository
    private var currentRule: ForwardRule? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuleEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = RuleRepository(this)

        setupUI()
        loadExistingRule()
    }

    private fun setupUI() {
        binding.btnSave.setOnClickListener { saveRule() }
    }

    private fun loadExistingRule() {
        currentRule = intent.getSerializableExtra("RULE") as? ForwardRule
        currentRule?.let {
            binding.apply {
                etRuleName.setText(it.ruleName)
                etLocalPort.setText(it.localPort.toString())
                etTargetAddress.setText(it.targetAddress)
                etTargetPort.setText(it.targetPort.toString())
                switchUdp.isChecked = it.isUDP
                switchActive.isChecked = it.isActive
            }
        }
    }

    private fun saveRule() {
        val newRule = ForwardRule(
            ruleName = binding.etRuleName.text.toString(),
            localPort = binding.etLocalPort.text.toString().toIntOrNull() ?: 0,
            targetAddress = binding.etTargetAddress.text.toString(),
            targetPort = binding.etTargetPort.text.toString().toIntOrNull() ?: 0,
            isUDP = binding.switchUdp.isChecked,
            isActive = binding.switchActive.isChecked
        )

        val updatedList = repository.loadRules().toMutableList().apply {
            removeIf { it.ruleName == newRule.ruleName }
            add(newRule)
        }
        repository.saveRules(updatedList)
        finish()
    }
}