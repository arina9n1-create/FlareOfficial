export enum UserRole {
  SUPER_ADMIN = "SUPER_ADMIN",
  ADMIN = "ADMIN",
  MANAGER = "MANAGER",
  MODERATOR = "MODERATOR",
  USER = "USER"
}

export interface AppUserEntity {
  uid: string;
  name: string;
  handle: string;
  email: string;
  role: UserRole;
  avatarType: string;
  coverType: string;
  bio: string;
  location: string;
  isBanned: boolean;
  banReason: string;
  canManageUsers: boolean;
  canDeletePosts: boolean;
  canEditPosts: boolean;
  canModerateComments: boolean;
  canManageChats: boolean;
  canManageMonetization: boolean;
  canManageRewards: boolean;
  canViewReports: boolean;
  canReviewReports: boolean;
  canGiveWarning: boolean;
  canDeleteReel: boolean;
  canDeleteVideo: boolean;
  canSuspendUser: boolean;
  canBanUser: boolean;
  canRemoveWarning: boolean;
  canViewWarningHistory: boolean;
  canViewActivityLog: boolean;
  isPublic: boolean;
  registeredAt: number;
  avatarPath?: string | null;
  coverPath?: string | null;
  avatarProvider: string;
  coverProvider: string;
}

export interface PostEntity {
  id: number;
  remoteId: string;
  username: string;
  userHandle: string;
  userAvatarType: string;
  userAvatarPath?: string | null;
  actionText: string;
  postImageRes: string;
  storagePath?: string | null;
  thumbnailPath?: string | null;
  caption: string;
  likesCount: number;
  isLiked: boolean;
  isSaved: boolean;
  isReposted: boolean;
  repostsCount: number;
  commentsCount: number;
  isPublic: boolean;
  isReelPost: boolean;
  timeAgo: string;
  timestamp: number;
  storageProvider: string;
  mimeType?: string | null;
  fileSize: number;
}

export interface ReelEntity {
  id: number;
  remoteId: string;
  author: string;
  handle: string;
  avatarType: string;
  userAvatarPath?: string | null;
  caption: string;
  music: string;
  imageRes: string;
  videoUrl?: string | null;
  storagePath?: string | null;
  thumbnailPath?: string | null;
  location: string;
  effectName?: string | null;
  likesCount: number;
  commentsCount: number;
  sharesCount: number;
  durationSecs: number;
  isLiked: boolean;
  isSaved: boolean;
  isPublic: boolean;
  timestamp: number;
  storageProvider: string;
  mimeType?: string | null;
  fileSize: number;
}

export interface StoryEntity {
  id: number;
  username: string;
  userAvatarType: string;
  userAvatarPath?: string | null;
  imageRes: string;
  storagePath?: string | null;
  caption: string;
  isOwn: boolean;
  timestamp: number;
  storageProvider: string;
  mimeType?: string | null;
  fileSize: number;
}

export interface ChatMessageEntity {
  id: number;
  remoteId: string;
  roomId: string;
  senderName: string;
  senderHandle: string;
  receiverHandle: string;
  senderAvatar: string;
  senderAvatarPath?: string | null;
  messageText: string;
  originalText: string;
  isTranslated: boolean;
  translationLang: string;
  mediaUrl?: string | null;
  storagePath?: string | null;
  storageProvider: string;
  mediaType: 'text' | 'image' | 'audio' | 'system';
  time: string;
  isFromMe: boolean;
  isRead: boolean;
  reactions: string;
  audioDurationSec: number;
  timestamp: number;
}

export interface NotificationEntity {
  id: number;
  username: string;
  recipientHandle: string;
  avatarType: string;
  actionText: string;
  timeAgo: string;
  isRead: boolean;
  timestamp: number;
}
