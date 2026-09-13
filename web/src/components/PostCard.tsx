import { Heart, MessageCircle, Repeat, Share2, Bookmark, MoreHorizontal } from 'lucide-react'
import { PostEntity } from '../types'
import { formatDistanceToNow } from 'date-fns'

interface PostCardProps {
  post: PostEntity
}

const PostCard = ({ post }: PostCardProps) => {
  return (
    <div className="card transition-all hover:shadow-md">
      {/* Post Header */}
      <div className="flex items-center justify-between p-4">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 overflow-hidden rounded-full border border-gray-100">
            <img
              src={post.userAvatarPath || `https://ui-avatars.com/api/?name=${post.username}&background=random`}
              alt={post.username}
            />
          </div>
          <div className="flex flex-col">
            <div className="flex items-center gap-1">
              <span className="font-bold text-sm">{post.username}</span>
              <span className="text-xs text-textSecondary">@{post.userHandle}</span>
            </div>
            <span className="text-[10px] text-textSecondary">
              {post.timestamp ? formatDistanceToNow(new Date(post.timestamp), { addSuffix: true }) : post.timeAgo}
            </span>
          </div>
        </div>
        <button className="rounded-full p-2 hover:bg-gray-50">
          <MoreHorizontal className="h-5 w-5 text-gray-400" />
        </button>
      </div>

      {/* Post Content */}
      <div className="px-4 pb-2">
        <p className="text-sm leading-relaxed whitespace-pre-wrap">{post.caption}</p>
      </div>

      {/* Post Media */}
      {(post.postImageRes || post.storagePath) && (
        <div className="px-2">
          <div className="overflow-hidden rounded-2xl bg-gray-50">
            <img
              src={post.storagePath || post.postImageRes}
              alt="Post media"
              className="max-h-[500px] w-full object-contain mx-auto"
            />
          </div>
        </div>
      )}

      {/* Post Actions */}
      <div className="flex items-center justify-between p-4 pt-2">
        <div className="flex items-center gap-6">
          <button className={`flex items-center gap-1 group`}>
            <div className={`rounded-full p-2 group-hover:bg-red-50 transition-colors`}>
              <Heart className={`h-6 w-6 ${post.isLiked ? 'fill-red-500 text-red-500' : 'text-gray-600 group-hover:text-red-500'}`} />
            </div>
            <span className={`text-xs font-bold ${post.isLiked ? 'text-red-500' : 'text-gray-600'}`}>{post.likesCount}</span>
          </button>

          <button className="flex items-center gap-1 group">
            <div className="rounded-full p-2 group-hover:bg-blue-50 transition-colors">
              <MessageCircle className="h-6 w-6 text-gray-600 group-hover:text-blue-500" />
            </div>
            <span className="text-xs font-bold text-gray-600 group-hover:text-blue-500">{post.commentsCount}</span>
          </button>

          <button className="flex items-center gap-1 group">
            <div className="rounded-full p-2 group-hover:bg-green-50 transition-colors">
              <Repeat className={`h-6 w-6 ${post.isReposted ? 'text-green-500' : 'text-gray-600 group-hover:text-green-500'}`} />
            </div>
            <span className="text-xs font-bold text-gray-600 group-hover:text-green-500">{post.repostsCount}</span>
          </button>

          <button className="rounded-full p-2 hover:bg-gray-100 transition-colors">
            <Share2 className="h-6 w-6 text-gray-600" />
          </button>
        </div>

        <button className="rounded-full p-2 hover:bg-gray-100 transition-colors">
          <Bookmark className={`h-6 w-6 ${post.isSaved ? 'fill-primary text-primary' : 'text-gray-600'}`} />
        </button>
      </div>
    </div>
  )
}

export default PostCard