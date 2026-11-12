package io.github.kemurixd.lurk.chat

import io.github.kemurixd.lurk.core.model.ChatMessage
import io.github.kemurixd.lurk.core.model.ChatUser
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.FfzEffect
import io.github.kemurixd.lurk.core.model.MessagePart
import io.github.kemurixd.lurk.core.model.MessagePart.EmotePart
import io.github.kemurixd.lurk.core.model.MessagePart.Text
import io.github.kemurixd.lurk.core.model.NativeEmoteSpan
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.emotes.EmoteRegistry
import io.github.kemurixd.lurk.emotes.EmoteSnapshot
import io.github.kemurixd.lurk.emotes.ProviderSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Instant

private fun emote(
    name: String,
    provider: EmoteProvider = EmoteProvider.SEVENTV,
    zeroWidth: Boolean = false,
    effects: Set<FfzEffect> = emptySet(),
    listed: Boolean = true,
    contentFlags: Int = 0,
    scope: EmoteScope = EmoteScope.CHANNEL,
) = Emote(
    id = "id-$name",
    name = name,
    provider = provider,
    scope = scope,
    images = listOf(EmoteImage(1, "https://cdn.example/$name/1x")),
    zeroWidth = zeroWidth,
    listed = listed,
    contentFlags = contentFlags,
    modifierEffects = effects,
)

private val KEKW = emote("KEKW")
private val A = emote("A")
private val B = emote("B")
private val Z1 = emote("Z1", zeroWidth = true)
private val Z2 = emote("Z2", zeroWidth = true)
private val FFZ_X = emote("ffzX", EmoteProvider.FFZ, effects = setOf(FfzEffect.FLIP_X))
private val FFZ_Y = emote("ffzY", EmoteProvider.FFZ, effects = setOf(FfzEffect.FLIP_Y))
private val FFZ_NO_SPACE = emote("ffzNoSpace", EmoteProvider.FFZ, effects = setOf(FfzEffect.NO_SPACE))
private val NATIVE_KAPPA = emote("Kappa", EmoteProvider.TWITCH)

private val SENDER = ChatUser(id = "u1", login = "viewer1", displayName = "Viewer1")
private val OTHER = ChatUser(id = "u2", login = "viewer2", displayName = "Viewer2")

private fun snapshot(
    vararg emotes: Emote,
    personal: Map<String, List<Emote>> = emptyMap(),
): EmoteSnapshot =
    EmoteRegistry()
        .apply {
            emotes.groupBy { it.provider }.forEach { (provider, list) ->
                put(ProviderSet(provider, EmoteScope.CHANNEL, list))
            }
            personal.forEach { (user, list) -> putPersonal(user, list) }
        }.snapshot.value

private fun message(
    text: String,
    nativeEmotes: List<NativeEmoteSpan> = emptyList(),
    bits: Int = 0,
    sender: ChatUser? = SENDER,
) = ChatMessage(
    id = "m1",
    platform = Platform.TWITCH,
    channelId = "c1",
    sender = sender,
    text = text,
    sentAt = Instant.fromEpochMilliseconds(0),
    nativeEmotes = nativeEmotes,
    bits = bits,
)

/** The original text each part stands for, joined in order. */
private fun List<MessagePart>.source(): String =
    joinToString("") {
        when (it) {
            is Text -> it.text
            is EmotePart -> it.source
            is MessagePart.Mention -> it.source
            is MessagePart.Link -> it.source
            is MessagePart.Cheer -> it.source
            is MessagePart.Emoji -> it.text
        }
    }

private val ALL =
    snapshot(KEKW, A, B, Z1, Z2, FFZ_X, FFZ_Y, FFZ_NO_SPACE)

class TokenizerTest {
    private val tokenizer = Tokenizer()

    private fun tokenize(
        text: String,
        emotes: EmoteSnapshot = ALL,
        applyFfzEffects: Boolean = true,
        nativeEmotes: List<NativeEmoteSpan> = emptyList(),
        bits: Int = 0,
        sender: ChatUser? = SENDER,
    ): List<MessagePart> = tokenizer.tokenize(message(text, nativeEmotes, bits, sender), emotes, applyFfzEffects)

    /** Tokenizes [input] and checks the parts still spell [expectedSource] (the input when nothing was removed). */
    private fun tokenizeChecked(
        input: String,
        expectedSource: String = input,
        applyFfzEffects: Boolean = true,
        nativeEmotes: List<NativeEmoteSpan> = emptyList(),
    ): List<MessagePart> {
        val parts = tokenize(input, applyFfzEffects = applyFfzEffects, nativeEmotes = nativeEmotes)
        assertEquals(expectedSource, parts.source(), "source of $parts")
        return parts
    }

    @Test
    fun registryEmoteBecomesEmotePartAndUnknownWordsStayText() {
        assertEquals(listOf(Text("hi "), EmotePart(KEKW, source = "KEKW")), tokenizeChecked("hi KEKW"))
        assertEquals(listOf(Text("hi there kekw")), tokenizeChecked("hi there kekw"))
    }

    @Test
    fun emptyMessageHasNoParts() {
        assertEquals(emptyList(), tokenize(""))
    }

    @Test
    fun runsOfSpacesAreKeptAsText() {
        assertEquals(
            listOf(Text("  a   "), EmotePart(KEKW, source = "KEKW"), Text("  ")),
            tokenizeChecked("  a   KEKW  "),
        )
    }

    @Test
    fun tabsStayInsideWords() {
        assertEquals(listOf(Text("KEKW\tx")), tokenizeChecked("KEKW\tx"))
    }

    @Test
    fun nativeSpanWinsOverRegistryEmoteWithSameName() {
        val registryKappa = emote("Kappa")
        val spans = listOf(NativeEmoteSpan(3, 8, NATIVE_KAPPA))
        assertEquals(
            listOf(Text("hi "), EmotePart(NATIVE_KAPPA, source = "Kappa")),
            tokenize("hi Kappa", snapshot(registryKappa), nativeEmotes = spans),
        )
        assertEquals(
            listOf(Text("hi "), EmotePart(registryKappa, source = "Kappa")),
            tokenize("hi Kappa", snapshot(registryKappa)),
        )
    }

    @Test
    fun nativeSpanSplitsTheWordItSitsIn() {
        val parts = tokenizeChecked("xKappay", nativeEmotes = listOf(NativeEmoteSpan(1, 6, NATIVE_KAPPA)))
        assertEquals(listOf(Text("x"), EmotePart(NATIVE_KAPPA, source = "Kappa"), Text("y")), parts)
    }

    @Test
    fun invalidNativeSpansFallBackToText() {
        val outOfRange = tokenizeChecked("hi", nativeEmotes = listOf(NativeEmoteSpan(1, 9, NATIVE_KAPPA)))
        assertEquals(listOf(Text("hi")), outOfRange)
        val negative = tokenizeChecked("hi", nativeEmotes = listOf(NativeEmoteSpan(-1, 1, NATIVE_KAPPA)))
        assertEquals(listOf(Text("hi")), negative)
        val empty = tokenizeChecked("hi", nativeEmotes = listOf(NativeEmoteSpan(1, 1, NATIVE_KAPPA)))
        assertEquals(listOf(Text("hi")), empty)
        val overlapping =
            tokenizeChecked(
                "Kappa Kappa",
                nativeEmotes =
                    listOf(
                        NativeEmoteSpan(0, 5, NATIVE_KAPPA),
                        NativeEmoteSpan(3, 8, NATIVE_KAPPA),
                        NativeEmoteSpan(6, 11, NATIVE_KAPPA),
                    ),
            )
        assertEquals(
            listOf(
                EmotePart(NATIVE_KAPPA, source = "Kappa"),
                Text(" "),
                EmotePart(NATIVE_KAPPA, source = "Kappa"),
            ),
            overlapping,
        )
    }

    @Test
    fun personalEmoteAppliesOnlyToItsOwner() {
        val mine = emote("Mine", scope = EmoteScope.PERSONAL)
        val emotes = snapshot(personal = mapOf(SENDER.id to listOf(mine)))
        assertEquals(listOf(EmotePart(mine, source = "Mine")), tokenize("Mine", emotes, sender = SENDER))
        assertEquals(listOf(Text("Mine")), tokenize("Mine", emotes, sender = OTHER))
        assertEquals(listOf(Text("Mine")), tokenize("Mine", emotes, sender = null))
    }

    @Test
    fun stackedOverlays() {
        assertEquals(
            listOf(EmotePart(A, overlays = listOf(Z1, Z2), source = "A Z1 Z2")),
            tokenizeChecked("A Z1 Z2"),
        )
    }

    @Test
    fun overlaySourceKeepsEverySpace() {
        assertEquals(
            listOf(Text("x "), EmotePart(A, overlays = listOf(Z1), source = "A   Z1"), Text(" y")),
            tokenizeChecked("x A   Z1 y"),
        )
    }

    @Test
    fun zeroWidthAtStartStandsAlone() {
        assertEquals(listOf(EmotePart(Z1, source = "Z1"), Text(" hi")), tokenizeChecked("Z1 hi"))
        assertEquals(listOf(Text("hi "), EmotePart(Z1, source = "Z1")), tokenizeChecked("hi Z1"))
    }

    @Test
    fun zeroWidthAfterLoneZeroWidthStacksOntoIt() {
        assertEquals(
            listOf(EmotePart(Z1, overlays = listOf(Z2), source = "Z1 Z2")),
            tokenizeChecked("Z1 Z2"),
        )
    }

    @Test
    fun textBetweenBlocksTheOverlay() {
        assertEquals(
            listOf(EmotePart(A, source = "A"), Text(" x "), EmotePart(Z1, source = "Z1")),
            tokenizeChecked("A x Z1"),
        )
    }

    @Test
    fun mentionBeforeZeroWidthBlocksTheOverlay() {
        assertEquals(
            listOf(MessagePart.Mention("bob", "@bob"), Text(" "), EmotePart(Z1, source = "Z1")),
            tokenizeChecked("@bob Z1"),
        )
    }

    @Test
    fun zeroWidthAttachesToNativeEmote() {
        assertEquals(
            listOf(EmotePart(NATIVE_KAPPA, overlays = listOf(Z1), source = "Kappa Z1")),
            tokenizeChecked("Kappa Z1", nativeEmotes = listOf(NativeEmoteSpan(0, 5, NATIVE_KAPPA))),
        )
    }

    @Test
    fun zeroWidthGluedToNativeEmoteAttaches() {
        assertEquals(
            listOf(EmotePart(NATIVE_KAPPA, overlays = listOf(Z1), source = "KappaZ1")),
            tokenizeChecked("KappaZ1", nativeEmotes = listOf(NativeEmoteSpan(0, 5, NATIVE_KAPPA))),
        )
    }

    @Test
    fun modifierAddsItsEffectToThePreviousEmote() {
        assertEquals(
            listOf(EmotePart(A, effects = setOf(FfzEffect.FLIP_X), source = "A")),
            tokenizeChecked("A ffzX", expectedSource = "A"),
        )
    }

    @Test
    fun modifiersCombine() {
        assertEquals(
            listOf(EmotePart(A, effects = setOf(FfzEffect.FLIP_X, FfzEffect.FLIP_Y), source = "A"), Text(" ok")),
            tokenizeChecked("A ffzX  ffzY ok", expectedSource = "A ok"),
        )
    }

    @Test
    fun modifierWithoutTargetStandsAlone() {
        assertEquals(listOf(EmotePart(FFZ_X, source = "ffzX"), Text(" hi")), tokenizeChecked("ffzX hi"))
        assertEquals(
            listOf(Text("hi "), EmotePart(FFZ_X, source = "ffzX")),
            tokenizeChecked("hi ffzX"),
        )
    }

    @Test
    fun modifierRendersAsEmoteWhenEffectsAreOff() {
        assertEquals(
            listOf(EmotePart(A, source = "A"), Text(" "), EmotePart(FFZ_X, source = "ffzX")),
            tokenizeChecked("A ffzX", applyFfzEffects = false),
        )
    }

    @Test
    fun noSpaceDropsTheSpaceBeforeTheTarget() {
        assertEquals(
            listOf(EmotePart(B, source = "B"), EmotePart(A, effects = setOf(FfzEffect.NO_SPACE), source = "A")),
            tokenizeChecked("B A ffzNoSpace", expectedSource = "BA"),
        )
    }

    @Test
    fun noSpaceTrimsOnlyTheTrailingSpacesOfText() {
        assertEquals(
            listOf(Text("hi"), EmotePart(A, effects = setOf(FfzEffect.NO_SPACE), source = "A"), Text(" x")),
            tokenizeChecked("hi  A ffzNoSpace x", expectedSource = "hiA x"),
        )
    }

    @Test
    fun noSpaceOnFirstPartRemovesNothingElse() {
        assertEquals(
            listOf(EmotePart(A, effects = setOf(FfzEffect.NO_SPACE), source = "A")),
            tokenizeChecked("A ffzNoSpace", expectedSource = "A"),
        )
    }

    @Test
    fun zeroWidthAfterModifiedEmoteStillStacks() {
        assertEquals(
            listOf(EmotePart(A, overlays = listOf(Z1), effects = setOf(FfzEffect.FLIP_X), source = "A Z1")),
            tokenizeChecked("A ffzX Z1", expectedSource = "A Z1"),
        )
    }

    @Test
    fun modifierAfterStackedEmoteAppliesToTheStack() {
        assertEquals(
            listOf(EmotePart(A, overlays = listOf(Z1, Z2), effects = setOf(FfzEffect.FLIP_X), source = "A Z1 Z2")),
            tokenizeChecked("A Z1 Z2 ffzX", expectedSource = "A Z1 Z2"),
        )
    }

    @Test
    fun modifierAfterTextStandsAlone() {
        assertEquals(
            listOf(EmotePart(A, source = "A"), Text(" x "), EmotePart(FFZ_X, source = "ffzX")),
            tokenizeChecked("A x ffzX"),
        )
    }

    @Test
    fun modifierOnNativeEmote() {
        assertEquals(
            listOf(EmotePart(NATIVE_KAPPA, effects = setOf(FfzEffect.FLIP_X), source = "Kappa")),
            tokenizeChecked(
                "Kappa ffzX",
                expectedSource = "Kappa",
                nativeEmotes = listOf(NativeEmoteSpan(0, 5, NATIVE_KAPPA)),
            ),
        )
    }

    @Test
    fun cheerNeedsBits() {
        assertEquals(
            listOf(MessagePart.Cheer("cheer", 100, "Cheer100"), Text(" nice")),
            tokenize("Cheer100 nice", bits = 100),
        )
        assertEquals(listOf(Text("Cheer100 nice")), tokenize("Cheer100 nice", bits = 0))
    }

    @Test
    fun cheerUsesTheGivenPrefixes() {
        val parts =
            tokenizer.tokenize(
                message("Corgo50 cheer50 Corgo", bits = 50),
                ALL,
                cheerPrefixes = setOf("corgo"),
            )
        assertEquals(listOf(MessagePart.Cheer("corgo", 50, "Corgo50"), Text(" cheer50 Corgo")), parts)
    }

    @Test
    fun malformedCheersStayText() {
        assertEquals(
            listOf(Text("cheer cheer10x cheer99999999999 cheer0")),
            tokenize("cheer cheer10x cheer99999999999 cheer0", bits = 10),
        )
    }

    @Test
    fun mentionSplitsOffTrailingPunctuation() {
        assertEquals(
            listOf(MessagePart.Mention("User", "@User"), Text(", hi")),
            tokenizeChecked("@User, hi"),
        )
    }

    @Test
    fun bareAtSignIsText() {
        assertEquals(listOf(Text("@ @! a@b")), tokenizeChecked("@ @! a@b"))
    }

    @Test
    fun bareHostBecomesHttpsLink() {
        assertEquals(
            listOf(Text("see "), MessagePart.Link("https://lurk.dev/x", "lurk.dev/x")),
            tokenizeChecked("see lurk.dev/x"),
        )
    }

    @Test
    fun schemeLinkKeepsItsUrl() {
        assertEquals(
            listOf(MessagePart.Link("http://example.com/a?b=1", "http://example.com/a?b=1"), Text("!")),
            tokenizeChecked("http://example.com/a?b=1!"),
        )
    }

    @Test
    fun linkTrimsUnbalancedClosingParen() {
        assertEquals(
            listOf(MessagePart.Link("https://x.com/a", "https://x.com/a"), Text(").")),
            tokenizeChecked("https://x.com/a)."),
        )
        assertEquals(
            listOf(MessagePart.Link("https://x.com/a_(b)", "https://x.com/a_(b)")),
            tokenizeChecked("https://x.com/a_(b)"),
        )
    }

    @Test
    fun numbersAbbreviationsAndEmailsAreNotLinks() {
        assertEquals(listOf(Text("3.14")), tokenizeChecked("3.14"))
        assertEquals(listOf(Text("e.g.")), tokenizeChecked("e.g."))
        assertEquals(listOf(Text("test@example.com")), tokenizeChecked("test@example.com"))
        assertEquals(listOf(Text("https:// ...")), tokenizeChecked("https:// ..."))
    }

    @Test
    fun emojiWithSkinToneIsOneEmoji() {
        assertEquals(listOf(MessagePart.Emoji("👍🏽"), Text(" ok")), tokenizeChecked("👍🏽 ok"))
    }

    @Test
    fun zwjFamilyIsOneEmoji() {
        val family = "👨‍👩‍👧"
        assertEquals(listOf(MessagePart.Emoji(family)), tokenizeChecked(family))
    }

    @Test
    fun flagsAndVariationSelectorsStayInTheRun() {
        assertEquals(listOf(MessagePart.Emoji("🇯🇵")), tokenizeChecked("🇯🇵"))
        assertEquals(listOf(MessagePart.Emoji("❤️")), tokenizeChecked("❤️"))
    }

    @Test
    fun emojiGluedToTextSplits() {
        assertEquals(listOf(Text("hi"), MessagePart.Emoji("👍"), Text("x")), tokenizeChecked("hi👍x"))
    }

    @Test
    fun loneVariationSelectorIsText() {
        assertEquals(listOf(Text("a️")), tokenizeChecked("a️"))
    }

    @Test
    fun unlistedFlaggedEmoteIsKeptWithItsFlags() {
        val risky = emote("Risky", listed = false, contentFlags = (1 shl 16) or (1 shl 24))
        val part = tokenize("Risky", snapshot(risky)).single() as EmotePart
        assertSame(risky, part.emote)
        assertEquals(false, part.emote.listed)
        assertEquals(true, part.emote.isSexual)
        assertEquals(1 shl 24, part.emote.contentFlags and (1 shl 24))
    }

    @Test
    fun partsSpellTheInputMinusRemovedModifiers() {
        val input = "  @bob, KEKW Z1  hi👍🏽 see lurk.dev/x A  ffzX Z2 B A ffzNoSpace https://x.com/a). 3.14 "
        val parts =
            tokenizeChecked(
                input,
                expectedSource = "  @bob, KEKW Z1  hi👍🏽 see lurk.dev/x A Z2 BA https://x.com/a). 3.14 ",
            )
        assertEquals(
            listOf(
                Text("  "),
                MessagePart.Mention("bob", "@bob"),
                Text(", "),
                EmotePart(KEKW, overlays = listOf(Z1), source = "KEKW Z1"),
                Text("  hi"),
                MessagePart.Emoji("👍🏽"),
                Text(" see "),
                MessagePart.Link("https://lurk.dev/x", "lurk.dev/x"),
                Text(" "),
                EmotePart(A, overlays = listOf(Z2), effects = setOf(FfzEffect.FLIP_X), source = "A Z2"),
                Text(" "),
                EmotePart(B, source = "B"),
                EmotePart(A, effects = setOf(FfzEffect.NO_SPACE), source = "A"),
                Text(" "),
                MessagePart.Link("https://x.com/a", "https://x.com/a"),
                Text("). 3.14 "),
            ),
            parts,
        )
    }
}
