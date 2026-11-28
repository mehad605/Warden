package com.warden.app.feature.blocker.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.warden.app.R
import com.warden.app.databinding.ItemRuleEntryRowBinding
import com.warden.app.databinding.ItemRuleLetterHeaderBinding
import com.warden.app.feature.blocker.domain.RuleBadgeKind
import com.warden.app.feature.blocker.domain.RuleItemSpec
import com.warden.app.feature.blocker.domain.RuleListRow

/**
 * Shared virtualized [ListAdapter] rendering alphabetically grouped letter headers and
 * accessible rule rows for Blocked Words, Safe Phrases, and Website Domains.
 */
internal class RuleListAdapter(
    private val onBadgeClick: (RuleItemSpec) -> Unit = {},
    private val onActionClick: (RuleItemSpec) -> Unit,
) : ListAdapter<RuleListRow, RecyclerView.ViewHolder>(DiffCallback) {
    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is RuleListRow.Header -> VIEW_TYPE_HEADER
            is RuleListRow.Entry -> VIEW_TYPE_ENTRY
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemRuleLetterHeaderBinding.inflate(inflater, parent, false))
        } else {
            EntryViewHolder(ItemRuleEntryRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val row = getItem(position)) {
            is RuleListRow.Header -> (holder as HeaderViewHolder).bind(row)
            is RuleListRow.Entry -> (holder as EntryViewHolder).bind(row.spec, onBadgeClick, onActionClick)
        }
    }

    private class HeaderViewHolder(
        private val binding: ItemRuleLetterHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        internal fun bind(header: RuleListRow.Header) {
            binding.tvLetterHeader.text = header.letter
            binding.tvLetterCount.text =
                binding.root.context.getString(R.string.letter_group_count_fmt, header.count)
        }
    }

    private class EntryViewHolder(
        private val binding: ItemRuleEntryRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        internal fun bind(
            spec: RuleItemSpec,
            onBadgeClick: (RuleItemSpec) -> Unit,
            onActionClick: (RuleItemSpec) -> Unit,
        ) {
            binding.tvRuleTerm.text = spec.label
            bindBadge(spec, onBadgeClick)
            bindAction(spec, onActionClick)
        }

        private fun bindBadge(
            spec: RuleItemSpec,
            onBadgeClick: (RuleItemSpec) -> Unit,
        ) {
            val badge = binding.btnRuleBadge
            if (spec.badgeText.isNullOrEmpty() || spec.badgeKind == RuleBadgeKind.NONE) {
                badge.visibility = View.GONE
                badge.setOnClickListener(null)
                return
            }
            badge.visibility = View.VISIBLE
            badge.text = spec.badgeText
            badge.contentDescription = spec.badgeContentDesc.ifBlank { spec.badgeText }
            badge.isClickable = spec.badgeClickable
            badge.isFocusable = spec.badgeClickable
            applyBadgeColors(spec.badgeKind)
            badge.setOnClickListener(if (spec.badgeClickable) View.OnClickListener { onBadgeClick(spec) } else null)
        }

        private fun applyBadgeColors(kind: RuleBadgeKind) {
            val ctx = binding.root.context
            val (bg, fg) =
                when (kind) {
                    RuleBadgeKind.EXACT, RuleBadgeKind.USER ->
                        MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimaryContainer) to
                            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnPrimaryContainer)
                    RuleBadgeKind.CONTAINS ->
                        MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorErrorContainer) to
                            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnErrorContainer)
                    RuleBadgeKind.ALLOWED ->
                        ContextCompat.getColor(ctx, R.color.status_active_bg) to
                            ContextCompat.getColor(ctx, R.color.status_active)
                    RuleBadgeKind.BUILTIN, RuleBadgeKind.DISABLED, RuleBadgeKind.NONE ->
                        MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceVariant) to
                            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
                }
            binding.btnRuleBadge.backgroundTintList = ColorStateList.valueOf(bg)
            binding.btnRuleBadge.setTextColor(fg)
        }

        private fun bindAction(
            spec: RuleItemSpec,
            onActionClick: (RuleItemSpec) -> Unit,
        ) {
            val iconRes =
                if (spec.isSecondaryAction) {
                    R.drawable.baseline_refresh_24
                } else {
                    R.drawable.baseline_close_24
                }
            binding.btnRuleAction.setImageResource(iconRes)
            binding.btnRuleAction.contentDescription = spec.actionContentDesc
            binding.btnRuleAction.setOnClickListener { onActionClick(spec) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RuleListRow>() {
        override fun areItemsTheSame(
            oldItem: RuleListRow,
            newItem: RuleListRow,
        ): Boolean = oldItem.stableId == newItem.stableId

        override fun areContentsTheSame(
            oldItem: RuleListRow,
            newItem: RuleListRow,
        ): Boolean = oldItem == newItem
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ENTRY = 1
    }
}
