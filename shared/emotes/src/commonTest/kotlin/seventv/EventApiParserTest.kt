package io.github.kemurixd.lurk.emotes.seventv

import io.github.kemurixd.lurk.core.model.Badge
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.PaintFunction
import io.github.kemurixd.lurk.core.model.PaintShadow
import io.github.kemurixd.lurk.core.model.PaintStop
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.emotes.capturedLogs
import io.github.kemurixd.lurk.emotes.fixture
import io.github.kemurixd.lurk.emotes.provider.SevenTvParser
import io.github.kemurixd.lurk.emotes.provider.providerJson
import io.github.kemurixd.lurk.emotes.seventv.EventApiMessage.Ack
import io.github.kemurixd.lurk.emotes.seventv.EventApiMessage.Dispatch
import io.github.kemurixd.lurk.emotes.seventv.EventApiMessage.EndOfStream
import io.github.kemurixd.lurk.emotes.seventv.EventApiMessage.Heartbeat
import io.github.kemurixd.lurk.emotes.seventv.EventApiMessage.Hello
import io.github.kemurixd.lurk.emotes.seventv.EventApiMessage.Reconnect
import io.github.kemurixd.lurk.emotes.seventv.EventApiMessage.Unknown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class EventApiParserTest {
    private fun parse(name: String) = EventApiParser.parse(fixture("seventv/eventapi/$name"))

    private fun dispatch(name: String) = assertIs<Dispatch>(parse(name))

    @Test
    fun controlMessagesParse() {
        assertEquals(Hello("00000000000000000000000001", 48_441.milliseconds), parse("hello.json"))
        assertEquals(Heartbeat, parse("heartbeat.json"))
        assertEquals(Reconnect, parse("reconnect.json"))
        assertEquals(Ack("RESUME", true), parse("ack-resume.json"))
        assertEquals(Ack("RESUME", false), parse("ack-resume-failed.json"))
        assertEquals(EndOfStream(4006, "Server is restarting"), parse("end-of-stream.json"))
    }

    @Test
    fun dispatchesParse() {
        assertEquals("emote_set.update", dispatch("dispatch-emote-set-update.json").type)
        assertEquals("cosmetic.create", dispatch("dispatch-cosmetic-paint.json").type)
        assertEquals("cosmetic.create", dispatch("dispatch-cosmetic-badge.json").type)
        assertEquals("entitlement.create", dispatch("dispatch-entitlement-paint.json").type)
        assertEquals("entitlement.create", dispatch("dispatch-entitlement-emote-set.json").type)
        assertEquals("entitlement.create", dispatch("dispatch-entitlement-kick-only.json").type)
        assertEquals("entitlement.delete", dispatch("dispatch-entitlement-delete.json").type)
    }

    @Test
    fun garbageIsUnknown() {
        assertEquals(Unknown, EventApiParser.parse("garbage"))
        assertEquals(Unknown, EventApiParser.parse(""))
        assertEquals(Unknown, EventApiParser.parse("[1,2]"))
        assertEquals(Unknown, EventApiParser.parse("""{"op":"x"}"""))
        assertEquals(Unknown, EventApiParser.parse("""{"op":99,"d":{}}"""))
        assertEquals(Unknown, EventApiParser.parse("""{"op":0,"d":{"type":"emote_set.update"}}"""))
        assertEquals(Unknown, EventApiParser.parse("""{"op":1,"d":{"heartbeat_interval":1000}}"""))
    }

    @Test
    fun emoteSetUpdateGivesOneAddedAndOneRemoved() {
        val changes = SevenTvDispatch.changes(dispatch("dispatch-emote-set-update.json"), Platform.TWITCH)
        assertEquals(2, changes.size)
        val added = changes.filterIsInstance<SevenTvChange.EmoteAdded>().single()
        assertEquals("01AA0000000000000000000011", added.setId)
        assertEquals("pepeLaugh", added.emote.name)
        assertEquals("01EZPHVXS0000C438200A44F24", added.emote.id)
        assertEquals(EmoteScope.CHANNEL, added.emote.scope)
        assertEquals(
            SevenTvChange.EmoteRemoved("01AA0000000000000000000011", "01AA0000000000000000000E02"),
            changes.filterIsInstance<SevenTvChange.EmoteRemoved>().single(),
        )
    }

    @Test
    fun renameIsRemovedThenAdded() {
        val text =
            """
            {"op":0,"d":{"type":"emote_set.update","body":{"id":"set1","updated":[{"key":"emotes",
            "old_value":{"id":"e1","name":"old","data":{"host":{"url":"//cdn.7tv.app/emote/e1","files":[]}}},
            "value":{"id":"e1","name":"new","data":{"host":{"url":"//cdn.7tv.app/emote/e1","files":[]}}}}]}}}
            """.trimIndent()
        val changes = SevenTvDispatch.changes(assertIs<Dispatch>(EventApiParser.parse(text)), Platform.TWITCH)
        assertEquals(SevenTvChange.EmoteRemoved("set1", "e1"), changes[0])
        assertEquals("new", assertIs<SevenTvChange.EmoteAdded>(changes[1]).emote.name)
        assertEquals(2, changes.size)
    }

    @Test
    fun paintStopsAreArgb() {
        val change = SevenTvDispatch.changes(dispatch("dispatch-cosmetic-paint.json"), Platform.TWITCH).single()
        val paint = assertIs<SevenTvChange.PaintDefined>(change).paint
        assertEquals("01KDREVNA4XVTD3G04PWWDQMGF", paint.id)
        assertEquals("Northern Light", paint.name)
        assertEquals(PaintFunction.LINEAR_GRADIENT, paint.function)
        assertEquals(45, paint.angle)
        assertEquals(false, paint.repeat)
        assertEquals(null, paint.imageUrl)
        assertEquals(
            listOf(
                PaintStop(0.0f, 0xFF9C28A9.toInt()),
                PaintStop(0.2f, 0xFFEF5CFF.toInt()),
                PaintStop(0.4f, 0xFF5CFF9A.toInt()),
                PaintStop(0.6f, 0xFF4DFFED.toInt()),
                PaintStop(0.8f, 0xFF22596D.toInt()),
                PaintStop(1.0f, 0xFF22596D.toInt()),
            ),
            paint.stops,
        )
        assertEquals(listOf(PaintShadow(0f, 0f, 0.1f, 0xFF7CFBED.toInt())), paint.shadows)
    }

    @Test
    fun badgeUsesLargestWebp() {
        val change = SevenTvDispatch.changes(dispatch("dispatch-cosmetic-badge.json"), Platform.TWITCH).single()
        val defined = assertIs<SevenTvChange.BadgeDefined>(change)
        assertEquals("01GJGRX0FG0002GRV318PGDRKT", defined.id)
        assertEquals(
            Badge(
                setId = "01GJGRX0FG0002GRV318PGDRKT",
                version = "sub15",
                title = "7TV Subscriber (1 Year, 3 Months)",
                imageUrl = "https://cdn.7tv.app/badge/01GJGRX0FG0002GRV318PGDRKT/4x.webp",
            ),
            defined.badge,
        )
    }

    @Test
    fun entitlementsUsePlatformConnectionId() {
        assertEquals(
            listOf(SevenTvChange.Entitled("PAINT", "01KDREVNA4XVTD3G04PWWDQMGF", "1000500")),
            SevenTvDispatch.changes(dispatch("dispatch-entitlement-paint.json"), Platform.TWITCH),
        )
        assertEquals(
            listOf(SevenTvChange.Entitled("EMOTE_SET", "01AA0000000000000000000011", "1000500")),
            SevenTvDispatch.changes(dispatch("dispatch-entitlement-emote-set.json"), Platform.TWITCH),
        )
        assertEquals(
            listOf(SevenTvChange.Unentitled("PAINT", "01KDREVNA4XVTD3G04PWWDQMGF", "1000500")),
            SevenTvDispatch.changes(dispatch("dispatch-entitlement-delete.json"), Platform.TWITCH),
        )
    }

    @Test
    fun kickOnlyUserGivesNoChangeOnTwitch() {
        val kickOnly = dispatch("dispatch-entitlement-kick-only.json")
        assertTrue(SevenTvDispatch.changes(kickOnly, Platform.TWITCH).isEmpty())
        assertEquals(
            listOf(SevenTvChange.Entitled("PAINT", "01KDREVNA4XVTD3G04PWWDQMGF", "1000600")),
            SevenTvDispatch.changes(kickOnly, Platform.KICK),
        )
    }

    private fun cosmetic(obj: String) = """{"op":0,"d":{"type":"cosmetic.create","body":{"object":$obj}}}"""

    @Test
    fun malformedDispatchGivesNoChange() {
        val bad =
            listOf(
                cosmetic("""{"kind":"PAINT","data":{"id":"p","stops":"x"}}"""),
                cosmetic("""{"kind":"PAINT","data":{"id":"p","function":"SPIRAL"}}"""),
                """{"op":0,"d":{"type":"cosmetic.create","body":{"object":{"kind":"BADGE","data":"x"}}}}""",
                """{"op":0,"d":{"type":"entitlement.create","body":{"object":{"kind":"PAINT"}}}}""",
                """{"op":0,"d":{"type":"emote_set.update","body":{"pushed":"x"}}}""",
                """{"op":0,"d":{"type":"user.update","body":{}}}""",
            )
        bad.forEach { text ->
            val d = assertIs<Dispatch>(EventApiParser.parse(text))
            assertTrue(SevenTvDispatch.changes(d, Platform.TWITCH).isEmpty(), text)
        }
    }

    @Test
    fun decodeFailuresLogNoPayload() {
        val entitlement =
            """{"op":0,"d":{"type":"entitlement.create","body":{"object":{"kind":"PAINT",""" +
                """"user":{"username":"user_9999","connections":"x"}}}}}"""
        val logs =
            capturedLogs {
                val d = assertIs<Dispatch>(EventApiParser.parse(entitlement))
                assertTrue(SevenTvDispatch.changes(d, Platform.TWITCH).isEmpty())
                val badEmote = """{"id":"e1","name":"user_9999","data":{"host":"x"}}"""
                assertEquals(
                    null,
                    SevenTvParser.activeEmote(providerJson.parseToJsonElement(badEmote), EmoteScope.CHANNEL),
                )
                assertTrue(SevenTvParser.emoteSet("""{"emotes": [{"id": "user_9999""", EmoteScope.CHANNEL).isEmpty())
            }
        assertTrue(logs.isNotEmpty(), "the failures are still logged")
        logs.forEach { line -> assertFalse("user_9999" in line, line) }
    }
}
