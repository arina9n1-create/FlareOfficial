# Implementation Plan - Media Storage Architecture Upgrade

Upgrade Vyn9 media storage to use a stable, provider-independent `storage_path` system while maintaining B2 as the underlying provider and ensuring backward compatibility with legacy URL columns.

## User Review Required

> [!IMPORTANT]
> This upgrade changes how media is saved and resolved. `content://` URIs will no longer be stored in the database. Existing `content://` records will remain but new ones will be blocked at the repository/service layer.

## Proposed Changes

### Database & Remote Layer

#### [NEW] [media_architecture_final_hardening.sql](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/supabase/migrations/20260830010000_media_architecture_final_hardening.sql)
Add missing `storage_path` columns to Supabase tables.

#### [MODIFY] [SupabaseService.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/data/remote/SupabaseService.kt)
- Add validation to prevent `content://` URIs from being persisted.
- Ensure all `create` and `fetch` methods map `storage_path` and `user_avatar_path` correctly.

#### [MODIFY] [SocialRepository.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/data/repository/SocialRepository.kt)
- Update upload methods to generate and pass `storagePath`.
- Ensure all creation methods (`createPost`, `createReel`, etc.) accept the new path parameters.

### Logic & Utilities

#### [MODIFY] [MediaStorageResolver.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/util/MediaStorageResolver.kt)
- Centralize B2 URL resolution from `storage_path`.
- Maintain backward compatibility for legacy full URLs.

### UI & ViewModel

#### [MODIFY] [SocialViewModel.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/ui/viewmodel/SocialViewModel.kt)
- Update all media upload flows (Avatar, Cover, Post, Reel, Story, Chat) to correctly handle `storagePath`.
- Ensure `finalMediaUrl` is NOT a `content://` URI when persisting to Supabase.

#### [MODIFY] [CommonComponents.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/ui/components/CommonComponents.kt)
- Ensure `VynAvatar` and `VynImage` prioritize `storagePath` and pass it to the resolver.

#### [MODIFY] [ReelsVideoPlayerView.kt](file:///C:/Users/Feelings Of Hearts/AndroidStudioProjects/version9/app/src/main/java/com/example/media/player/ReelsVideoPlayerView.kt)
- Update to accept `storagePath` and use it for video resolution.

## Verification Plan

### Automated Tests
- Build the project using `./gradlew assembleDebug` to ensure no compilation errors.

### Manual Verification
1. **New Reel Upload**: Select a video, verify it uploads to B2, `storage_path` is saved in Supabase, and `video_url` is NOT `content://`.
2. **Reel Playback**: Verify the new Reel plays correctly using the resolver.
3. **Image Upload (Post/Avatar)**: Verify `storage_path` is saved and image renders correctly.
4. **Legacy Check**: Ensure old posts with full HTTP URLs still render.
