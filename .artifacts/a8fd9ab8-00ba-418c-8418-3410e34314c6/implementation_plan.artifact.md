# Fix Backblaze B2 Connection Mismatch

The app is experiencing connection issues with Backblaze B2 because the Supabase Edge Function names and request formats in the Android code do not match the deployed functions (`b2-upload`, `b2-download`, `b2-delete`).

## Proposed Changes

### [Component Name] Data Remote

#### [MODIFY] [SupabaseService.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/data/remote/SupabaseService.kt)
- Update `uploadMediaToB2` to call `/functions/v1/b2-upload` using a `MultipartBody` (required by the Edge Function).
- Update `deleteMediaFromB2` to call `/functions/v1/b2-delete` using a `POST` request with a JSON body containing the `path` (extracted from the media URL).
- Remove references to the non-existent `private-media-gateway`.

### [Component Name] Utilities

#### [MODIFY] [MediaStorageResolver.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/util/MediaStorageResolver.kt)
- Update the gateway URL to use `/functions/v1/b2-download` instead of `private-media-gateway`. This ensures Coil can correctly fetch and authenticate media requests.

## Verification Plan

### Manual Verification
- Deploy the updated code.
- Attempt to change the profile picture (triggers `b2-upload`).
- Verify that images (posts, avatars) render correctly (triggers `b2-download`).
- Delete a post and verify the media is removed from B2 (triggers `b2-delete`).
