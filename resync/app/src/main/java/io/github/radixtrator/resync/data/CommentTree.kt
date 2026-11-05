package io.github.radixtrator.resync.data

/** A comment or "more" row as shown, with how many rows are hidden under it when collapsed. */
data class VisibleNode(val node: CommentNode, val hiddenCount: Int)

object CommentTree {

    /** Drops descendants of collapsed comments. Relies on [all] being in depth-first order. */
    fun visible(all: List<CommentNode>, collapsed: Set<String>): List<VisibleNode> {
        val out = ArrayList<VisibleNode>(all.size)
        var hideDeeperThan = Int.MAX_VALUE
        var hidden = 0
        for (node in all) {
            if (node.depth > hideDeeperThan) {
                if (node is Comment) hidden++
                continue
            }
            if (hidden > 0) out[out.lastIndex] = out.last().copy(hiddenCount = hidden)
            hideDeeperThan = Int.MAX_VALUE
            hidden = 0
            out += VisibleNode(node, 0)
            if (node is Comment && node.id in collapsed) hideDeeperThan = node.depth
        }
        if (hidden > 0) out[out.lastIndex] = out.last().copy(hiddenCount = hidden)
        return out
    }

    /**
     * Replaces [more] in [all] with [loaded] (from /api/morechildren), fixing up depths from the
     * parent chain since that endpoint's depth values can't be relied on. [remaining] child IDs that
     * didn't fit in one request stay behind as a smaller "more" row.
     */
    fun splice(
        all: List<CommentNode>,
        more: MoreComments,
        loaded: List<CommentNode>,
        remaining: List<String>,
    ): List<CommentNode> {
        val index = all.indexOfFirst { it is MoreComments && it.id == more.id }
        if (index < 0) return all
        val existing = all.filterIsInstance<Comment>().mapTo(HashSet()) { it.id }
        val depths = all.associateTo(HashMap()) { "t1_${it.id}" to it.depth }

        val fresh = loaded.filter { it !is Comment || it.id !in existing }.map { node ->
            val depth = depths[node.parentId]?.plus(1) ?: more.depth
            if (node is Comment) depths["t1_${node.id}"] = depth
            when (node) {
                is Comment -> node.copy(depth = depth)
                is MoreComments -> node.copy(depth = depth)
            }
        }
        val leftover = if (remaining.isEmpty()) emptyList() else listOf(more.copy(children = remaining, count = remaining.size))
        return all.subList(0, index) + fresh + leftover + all.subList(index + 1, all.size)
    }
}
