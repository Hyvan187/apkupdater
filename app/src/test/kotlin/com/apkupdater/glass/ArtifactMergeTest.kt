package com.apkupdater.glass
import org.junit.Assert.*
import org.junit.Test
class ArtifactMergeTest {
    private val a = ArtifactIdentity("app", 42, "a".repeat(64), "IN-arm64", "official")
    @Test fun sameBytesFromTwoSourcesMerge() = assertEquals(1, ArtifactMerge.groups(listOf(a, a.copy(source = "mirror"))).size)
    @Test fun sameVersionWithoutHashIsNotIdentity() = assertEquals(2, ArtifactMerge.groups(listOf(a.copy(sha256 = null), a.copy(sha256 = null, source = "mirror"))).size)
    @Test fun regionalVariantsWithoutHashRemainSeparate() = assertEquals(2, ArtifactMerge.groups(listOf(a.copy(sha256 = null), a.copy(sha256 = null, variantId = "CN-arm64"))).size)
    @Test fun changedBytesDoNotMerge() = assertEquals(2, ArtifactMerge.groups(listOf(a, a.copy(sha256 = "b".repeat(64)))).size)
}
