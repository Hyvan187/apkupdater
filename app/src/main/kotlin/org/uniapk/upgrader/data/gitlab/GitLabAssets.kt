package org.uniapk.upgrader.data.gitlab


data class GitLabAssets(
    val sources: List<GitLabAsset>,
    val links: List<GitLabLink>
)
