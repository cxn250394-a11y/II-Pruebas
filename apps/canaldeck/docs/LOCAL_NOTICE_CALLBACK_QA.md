# Local pick notifications — author regression QA

This change belongs to the offline v0.4 candidate, child PR #8. It does not activate
the relay, use an account, or constitute independent ratification.

## Baseline reproduced

Against the parent-provided baseline `a102eb8433c227291b3ef1c9e2972456cc443871`,
the initial `LocalPickDeliveryTest` reproduced both defects with real Organization
preferences under Robolectric:

```text
LocalPickDeliveryTest > interleavedAlbumFragmentsCannotRenotifyTheSameAlbum FAILED
    java.lang.AssertionError at LocalPickDeliveryTest.kt:37
LocalPickDeliveryTest > reversedLinkCallbacksDoNotLoseTheLowerValidMessageId FAILED
    java.lang.AssertionError at LocalPickDeliveryTest.kt:26
2 tests completed, 2 failed
BUILD FAILED in 33s
```

The reproduced local run is preserved in
[`local-notice-red.log`](../evidence/round2-baseline/local-notice-red.log).
The tests are retained in source and expanded. Final CI evidence must
refer to the final PR HEAD; this document does not claim that CI or device tests
have passed before the coordinator records their results.

## Change and meaningful boundaries

- The owner's activation baseline remains fixed while link callbacks complete.
  Delivery callback order is never used as a per-chat high-water mark.
- A private SQLite ledger uses `(account, chat, message)` aliases and typed
  `(account, chat, message|album, identity)` units. Interleaved A/B/A album fragments
  remain one local notification, while independent messages and equal numeric
  album/message identities remain distinct. No text, caption, URL or media enters
  this ledger.
- The legacy last announced album is imported as an UNKNOWN tombstone on migration.
  Existing last-ID metadata is an activation boundary, not erased or advanced by
  a callback.
- Link requests hold only transient callbacks. No blocked/OFF content is persisted
  or added to a retry/backlog queue. Enabling Android notifications does not drain
  such a queue because none exists. The existing owner activation baseline and
  four-hour freshness check remain in force; this is not an absolute guarantee
  about every historical update TDLib might replay or delay.
- App permission, global notification state, individual channel importance,
  quiet hours, read state, source family, account/session identity and revisions
  are checked before notified dedup, publication and LOCAL_NOTIFIED accounting.
  A blocked or stale callback does not reserve a delivery or move its activation
  baseline. Changing a setting away and back invalidates the prior consent token.
- Content/edit/delete/read updates must invalidate the old client snapshot or
  establish an immediate read fence before asynchronous refresh. The caller also
  requires the exact still-current message snapshot when its link resolves.
- A notification request reserves PENDING immediately before the Android boundary.
  A reported interruption becomes UNKNOWN; unresolved PENDING becomes UNKNOWN at
  session startup. Neither state is automatically retried or erased.

## Tests and limits

The regression suite covers reverse callback orders through the actual dispatcher,
first live IDs after activation/reconnect, interleaved albums and restart, typed
identities, source-message aliases, app/channel OFF, final publication blocking,
account/session/quiet/read/snapshot changes, same-mode consent changes, invalid
revision sentinels, UNKNOWN persistence, and upgrade of the last legacy album.

Four instrumented Android tests exercise actual SQLite and deferred Fake link
callbacks without creating a TDLib client. Their ContextWrapper prefixes all
fixture databases and preferences so cleanup cannot delete existing app data.

Android `notify()` and SQLite cannot form a shared transaction. A crash after
reservation but before notification may lose that local notification; preserving
UNKNOWN avoids blind duplicates. A successful local request is not proof that
Android displayed or sounded an alert, nor any Telegram or Pick$ delivery. OS
permission changes that occur outside observable broadcasts/checks and Telegram
updates not yet received remain physical validation concerns. All relay output
remains compiled OFF; no real delivery was exercised by this QA.
