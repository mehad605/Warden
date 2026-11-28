package com.warden.app.feature.blocker.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.warden.app.R
import com.warden.app.databinding.ItemRuleEntryRowBinding
import com.warden.app.databinding.ItemRuleLetterHeaderBinding
import com.warden.app.feature.blocker.domain.RuleActionKind
import com.warden.app.feature.blocker.domain.RuleBadgeKind
import com.warden.app.feature.blocker.domain.RuleItemSpec
import com.warden.app.feature.blocker.domain.RuleListRow

/**
 * Shared virtualized [RecyclerView.Adapter] rendering alphabetically grouped letter headers and
 * accessible rule rows for Blocked Words, Safe Phrases, and Website Domains.
 *
 * Uses [DiffUtil] for smooth animations on small/medium lists (`<= 500` rows) and switches to an
 * O(1) swap for massive datasets (46,000+ built-in domains) so scrolling and search never stutter.
 */
internal class RuleListAdapter(
    private val onBadgeClick: (RuleItemSpec) -> Unit = {},
    private val onActionClick: (RuleItemSpec) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var rows: List<RuleListRow> = emptyList()

    /** Replaces the displayed [newRows], using [DiffUtil] for lists up to 500 rows. */
    @SuppressLint("NotifyDataSetChanged")
    internal fun submitList(newRows: List<RuleListRow>) {
        val oldRows = rows
        if (oldRows === newRows) return
        if (oldRows.size <= DIFF_LIMIT && newRows.size <= DIFF_LIMIT) {
            val diff = DiffUtil.calculateDiff(RowDiffCallback(oldRows, newRows))
            rows = newRows
            diff.dispatchUpdatesTo(this)
        } else {
            rows = newRows
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int =
        when (rows[position]) {
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
        when (val row = rows[position]) {
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
            badge.contentDescription =
                if (spec.badgeClickable) {
                    binding.root.context.getString(R.string.cd_toggle_mode_fmt, spec.label, spec.badgeText)
                } else {
                    spec.badgeText
                }
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
            val ctx = binding.root.context
            val iconRes =
                if (spec.actionKind == RuleActionKind.ENABLE_BUILTIN) {
                    R.drawable.baseline_refresh_24
                } else {
                    R.drawable.baseline_close_24
                }
            val descRes =
                when (spec.actionKind) {
                    RuleActionKind.DELETE -> R.string.cd_delete_entry_fmt
                    RuleActionKind.DISABLE_BUILTIN -> R.string.cd_disable_builtin_fmt
                    RuleActionKind.ENABLE_BUILTIN -> R.string.cd_enable_builtin_fmt
                }
            binding.btnRuleAction.setImageResource(iconRes)
            binding.btnRuleAction.contentDescription = ctx.getString(descRes, spec.label)
            binding.btnRuleAction.setOnClickListener { onActionClick(spec) }
        }
    }

    private class RowDiffCallback(
        private val oldRows: List<RuleListRow>,
        private val newRows: List<RuleListRow>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldRows.size

        override fun getNewListSize(): Int = newRows.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean = oldRows[oldItemPosition].stableId == newRows[newItemPosition].stableId

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean = oldRows[oldItemPosition] == newRows[newItemPosition]
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ENTRY = 1
        private const val DIFF_LIMIT = 500
    }
}
