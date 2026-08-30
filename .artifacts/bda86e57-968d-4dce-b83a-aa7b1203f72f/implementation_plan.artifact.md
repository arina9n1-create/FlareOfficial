# Stable Storage Path Architecture Implementation Plan

This plan outlines the migration from provider-specific URLs and temporary `content://` URIs to a provider-independent stable storage path architecture in the Vyn9 app.

## User Review Required

> [!IMPORTANT]
> This change introduces a centralized `MediaStorageResolver`. Any new UI components displaying media MUST use this resolver instead of accessing URL/Res fields directly.

> [!NOTE]
> Database migrations (Version 11) for Room are already in place, but some DAO queries and synchronization logic need refinement to prioritize `storagePath`.

## Proposed Changes

### [Component: Data Models & Database]

#### [MODIFY] [Models.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/data/model/Models.kt)
- Ensure all media-related entities (`AppUserEntity`, `PostEntity`, `ReelEntity`, `StoryEntity`, `ChatMessageEntity`, `UserProfileEntity`, `FriendEntity`) have `storagePath` and `thumbnailPath` fields (most are already present).

#### [MODIFY] [AppDatabase.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/data/db/AppDatabase.kt)
- Update `SocialDao` queries to include `storagePath` in filters and ensure posts/reels/stories with only `storagePath` are correctly fetched.

### [Component: Remote & Services]

#### [MODIFY] [SupabaseService.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/data/remote/SupabaseService.kt)
- Update `observeChatRealtime` to include `storage_path` and `sender_avatar_path`.
- Ensure all JSON mapping in `fetch...` and `create...` methods handles `storage_path` and `thumbnail_path` consistently.

#### [MODIFY] [SocialRepository.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/data/repository/SocialRepository.kt)
- Update `syncPostsFromSupabase` and `syncReelsFromSupabase` to not filter out items that have a valid `storagePath` but might have a placeholder `postImageRes`/`videoUrl`.
- Ensure `updateProfile` and `registerOrSyncUser` correctly handle `avatarPath` and `coverPath`.

### [Component: Utilities]

#### [MODIFY] [MediaStorageResolver.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/util/MediaStorageResolver.kt)
- Strengthen the `resolve` logic to handle all edge cases:
    - Return empty for null/blank.
    - Return original for presets (`default`, `none`, `null`).
    - Return original for `content://`, `file://`, `http://`, `https://`.
    - Construct gateway URL for stable paths (e.g., `posts/uid/postid/file.jpg`).

### [Component: UI & ViewModels]

#### [MODIFY] [SocialViewModel.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/ui/viewmodel/SocialViewModel.kt)
- Ensure all upload methods (`createPost`, `uploadReel`, `changeProfilePhoto`, `changeCoverPhoto`, `addStory`, `sendChatMessage`) correctly capture `storagePath` from `MediaUploadResult` and persist it.

#### [MODIFY] [ReelsVideoPlayerView.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/media/player/ReelsVideoPlayerView.kt)
- Use `MediaStorageResolver.resolve` on the input URL/Path to ensure stable paths are resolved before playback.

#### [MODIFY] [ReelsScreen.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/ui/screens/ReelsScreen.kt)
- Update pre-caching logic in `LaunchedEffect` to resolve URLs before passing them to `ExoPlayerCacheManager`.

## Verification Plan

### Automated Tests
- Build the project to ensure no compilation errors.
- Run existing unit tests if applicable.

### Manual Verification
1. **Profile Media**: Change avatar and cover photo, verify `storage_path` is saved in Supabase and display works.
2. **Posts**: Create a new post with an image, verify `storage_path` is present in DB.
3. **Reels**: Upload a reel, verify video and thumbnail `storage_path` are saved.
4. **Chat**: Send an image in chat, verify stable path is used.
5. **Backwards Compatibility**: Check existing posts/avatars with full URLs to ensure they still load.
6. **Unavailable State**: Simulate a `content://` URI in the DB (legacy) and ensure the resolver/UI handles it gracefully (or resolver returns it and Coil handles it).
