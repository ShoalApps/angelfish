package io.github.shoalapps.swordfish.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** InnerTube responses trimmed down to the parts Angelfish reads. */
class YtiTest {
    private fun parse(s: String): JsonElement = Json.parseToJsonElement(s)

    @Test fun classicVideoRenderer() {
        val root = parse(
            """
            {"contents":{"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[{"videoRenderer":{
              "videoId":"dQw4w9WgXcQ",
              "title":{"runs":[{"text":"Never Gonna "},{"text":"Give You Up"}]},
              "ownerText":{"runs":[{"text":"Rick Astley","navigationEndpoint":{"browseEndpoint":{"browseId":"UCuAXFkgsw1L7xaCfnd5JJOw"}}}]},
              "thumbnail":{"thumbnails":[{"url":"//i.ytimg.com/small.jpg"},{"url":"//i.ytimg.com/big.jpg"}]},
              "lengthText":{"simpleText":"3:33"},
              "shortViewCountText":{"simpleText":"1.7B views"},
              "publishedTimeText":{"simpleText":"15 years ago"}
            }}]}}]}}}
            """,
        )
        val v = Yti.videos(root).single()
        assertEquals("dQw4w9WgXcQ", v.id)
        assertEquals("Never Gonna Give You Up", v.title)
        assertEquals("Rick Astley", v.channelName)
        assertEquals("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw", v.channelUrl)
        assertEquals("https://i.ytimg.com/big.jpg", v.thumbnail)
        assertEquals(213, v.durationSec)
        assertEquals("1.7B views", v.viewsText)
        assertEquals("15 years ago", v.uploaded)
        assertEquals(false, v.isLive)
    }

    @Test fun lockupViewModel() {
        val root = parse(
            """
            {"items":[{"lockupViewModel":{
              "contentType":"LOCKUP_CONTENT_TYPE_VIDEO","contentId":"abcdefghijk",
              "contentImage":{"thumbnailViewModel":{"image":{"sources":[{"url":"https://i.ytimg.com/a.jpg"}]},
                "overlays":[{"thumbnailOverlayBadgeViewModel":{"thumbnailBadges":[{"thumbnailBadgeViewModel":{"text":"12:01"}}]}}]}},
              "metadata":{"lockupMetadataViewModel":{
                "title":{"content":"A lockup video"},
                "metadata":{"contentMetadataViewModel":{"metadataRows":[
                  {"metadataParts":[{"text":{"content":"Some Channel"}}]},
                  {"metadataParts":[{"text":{"content":"52K views"}},{"text":{"content":"2 days ago"}}]}
                ]}}
              }}
            }},{"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST","contentId":"PLxyz"}}]}
            """,
        )
        val v = Yti.videos(root).single()
        assertEquals("abcdefghijk", v.id)
        assertEquals("A lockup video", v.title)
        assertEquals("Some Channel", v.channelName)
        assertEquals(721, v.durationSec)
        assertEquals("52K views", v.viewsText)
        assertEquals("2 days ago", v.uploaded)
    }

    @Test fun shortsAreKeptOutOfTheVideoListAndFoundSeparately() {
        val root = parse(
            """
            {"a":[{"shortsLockupViewModel":{"entityId":"shorts-shelf-item-ZZZZZZZZZZZ",
               "overlayMetadata":{"primaryText":{"content":"A short"},"secondaryText":{"content":"9M views"}},
               "thumbnail":{"sources":[{"url":"https://i.ytimg.com/s.jpg"}]}}},
             {"reelItemRenderer":{"videoId":"YYYYYYYYYYY","headline":{"simpleText":"Reel"}}}]}
            """,
        )
        assertTrue(Yti.videos(root).isEmpty())
        val shorts = Yti.shorts(root)
        assertEquals(listOf("ZZZZZZZZZZZ", "YYYYYYYYYYY"), shorts.map { it.id })
        assertTrue(shorts.all { it.isShort && it.url.contains("/shorts/") })
        assertEquals("9M views", shorts.first().viewsText)
    }

    @Test fun liveVideosHaveNoDurationAndSayWatching() {
        val root = parse("""{"videoRenderer":{"videoId":"LLLLLLLLLLL","title":{"simpleText":"Live"},"viewCountText":{"runs":[{"text":"1.2K"},{"text":" watching"}]}}}""")
        val v = Yti.videos(parse("""{"x":$root}""")).single()
        assertTrue(v.isLive)
        assertEquals(-1, v.durationSec)
    }

    @Test fun subscriptionChannels() {
        val root = parse("""{"x":[{"channelRenderer":{"channelId":"UC1234567890123456789012","title":{"simpleText":"Chan"},"thumbnail":{"thumbnails":[{"url":"//a.jpg"}]}}}]}""")
        val sub = Yti.channels(root).single()
        assertEquals("https://www.youtube.com/channel/UC1234567890123456789012", sub.url)
        assertEquals("Chan", sub.name)
        assertEquals("https://a.jpg", sub.avatar)
    }

    @Test fun continuationToken() {
        val root = parse("""{"a":{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"TOKEN"}}}}}""")
        assertEquals("TOKEN", Yti.continuation(root))
    }

    @Test fun commentActionsFromTheNewerLayout() {
        val root = parse(
            """
            {"contents":[{"commentThreadRenderer":{"commentViewModel":{"commentViewModel":{
                "commentId":"UgxABC","toolbarSurfaceKey":"surface-abc"}}}}],
             "frameworkUpdates":{"entityBatchUpdate":{"mutations":[
               {"entityKey":"surface-other","payload":{"engagementToolbarSurfaceEntityPayload":{
                 "likeCommand":{"innertubeCommand":{"performCommentActionEndpoint":{"action":"WRONG"}}}}}},
               {"entityKey":"surface-abc","payload":{"engagementToolbarSurfaceEntityPayload":{
                 "likeCommand":{"innertubeCommand":{"performCommentActionEndpoint":{"action":"LIKE1"}}},
                 "unlikeCommand":{"innertubeCommand":{"performCommentActionEndpoint":{"action":"UNLIKE1"}}},
                 "dislikeCommand":{"innertubeCommand":{"performCommentActionEndpoint":{"action":"DISLIKE1"}}},
                 "undislikeCommand":{"innertubeCommand":{"performCommentActionEndpoint":{"action":"UNDISLIKE1"}}}}}}
             ]}}}
            """,
        )
        assertEquals(
            mapOf("like" to "LIKE1", "unlike" to "UNLIKE1", "dislike" to "DISLIKE1", "undislike" to "UNDISLIKE1"),
            Yti.commentActions(root, "UgxABC"),
        )
    }

    @Test fun commentActionsFromTheOlderLayout() {
        val root = parse(
            """
            {"items":[{"commentRenderer":{"commentId":"UgxOLD","actionButtons":{"commentActionButtonsRenderer":{
              "likeButton":{"toggleButtonRenderer":{
                "defaultServiceEndpoint":{"performCommentActionEndpoint":{"action":"L"}},
                "toggledServiceEndpoint":{"performCommentActionEndpoint":{"action":"UL"}}}},
              "dislikeButton":{"toggleButtonRenderer":{
                "defaultServiceEndpoint":{"performCommentActionEndpoint":{"action":"D"}},
                "toggledServiceEndpoint":{"performCommentActionEndpoint":{"action":"UD"}}}}}}}}]}
            """,
        )
        assertEquals(mapOf("like" to "L", "unlike" to "UL", "dislike" to "D", "undislike" to "UD"), Yti.commentActions(root, "UgxOLD"))
    }

    @Test fun commentActionsForAMissingCommentAreEmpty() {
        assertTrue(Yti.commentActions(parse("""{"a":{"commentId":"other"}}"""), "UgxNONE").isEmpty())
    }
}
