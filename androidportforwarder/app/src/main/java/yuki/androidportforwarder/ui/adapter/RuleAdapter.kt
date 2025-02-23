package yuki.androidportforwarder.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import yuki.androidportforwarder.data.model.ForwardRule
import yuki.androidportforwarder.databinding.ItemRuleBinding

class RuleAdapter : ListAdapter<ForwardRule, RuleAdapter.ViewHolder>(DiffCallback()) {
    var onItemClick: (ForwardRule) -> Unit = {}
    var onSwitchChanged: (ForwardRule, Boolean) -> Unit = { _, _ -> }

    inner class ViewHolder(val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(rule: ForwardRule) {
            binding.apply {
                ruleName.text = rule.ruleName
                localPort.text = rule.localPort.toString()
                targetInfo.text = "${rule.targetAddress}:${rule.targetPort}"
                protocol.text = if (rule.isUDP) "UDP" else "TCP"
                switchActive.isChecked = rule.isActive

                root.setOnClickListener { onItemClick(rule) }
                switchActive.setOnCheckedChangeListener { _, isChecked ->
                    onSwitchChanged(rule, isChecked)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemRuleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<ForwardRule>() {
        override fun areItemsTheSame(oldItem: ForwardRule, newItem: ForwardRule) =
            oldItem.ruleName == newItem.ruleName

        override fun areContentsTheSame(oldItem: ForwardRule, newItem: ForwardRule) =
            oldItem == newItem
    }
}