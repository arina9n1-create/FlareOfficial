import { useState, useEffect } from 'react'
import { supabase } from '../lib/supabase'
import { AppUserEntity, PostEntity, ReelEntity, UserRole } from '../types'
import { SocialService } from '../services/SocialService'
import PostCard from '../components/PostCard'
import { Grid, PlayCircle, Users, Repeat, Bookmark, Edit, Share2, Plus, CheckCircle } from 'lucide-react'

const Profile = () => {
  const [profile, setProfile] = useState<AppUserEntity | null>(null)
  const [posts, setPosts] = useState<PostEntity[]>([])
  const [reels, setReels] = useState<ReelEntity[]>([])
  const [activeTab, setActiveTab] = useState('GRID')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const loadProfileData = async () => {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) return

      try {
        const profileData = await SocialService.getUserProfile(user.id)
        setProfile(profileData)

        // Fetch posts and reels for this user
        // (Following Android logic filtering by handle/name)
        const allPosts = await SocialService.fetchPosts()
        setPosts(allPosts.filter(p => p.userHandle === profileData?.handle))

        // In a real app, reels would be fetched from a service
        const { data: reelsData } = await supabase.from('reels').select('*').eq('handle', profileData?.handle)
        setReels(reelsData || [])

      } catch (error) {
        console.error('Error loading profile:', error)
      } finally {
        setLoading(false)
      }
    }

    loadProfileData()
  }, [])

  if (loading || !profile) {
    return (
      <div className="flex h-60 w-full items-center justify-center">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
      </div>
    )
  }

  const tabs = [
    { id: 'GRID', icon: Grid, label: 'Posts' },
    { id: 'REELS', icon: PlayCircle, label: 'Reels' },
    { id: 'FRIENDS', icon: Users, label: 'Friends' },
    { id: 'REPOSTS', icon: Repeat, label: 'Reposts' },
    { id: 'SAVED', icon: Bookmark, label: 'Saved' },
  ]

  return (
    <div className="mx-auto w-full max-w-2xl bg-white min-h-screen">
      {/* Header Section */}
      <div className="relative h-48 w-full bg-gray-200">
        {profile.coverPath ? (
          <img src={profile.coverPath} className="h-full w-full object-cover" alt="Cover" />
        ) : (
          <div className="h-full w-full bg-gradient-to-br from-gray-100 to-gray-200" />
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent" />

        {/* Avatar */}
        <div className="absolute -bottom-12 left-4 h-24 w-24 rounded-full border-4 border-white bg-white shadow-lg overflow-hidden">
          <img
            src={profile.avatarPath || `https://ui-avatars.com/api/?name=${profile.name}&background=random`}
            className="h-full w-full object-cover"
            alt="Avatar"
          />
        </div>
      </div>

      {/* Profile Details */}
      <div className="mt-14 px-4 pb-4">
        <div className="flex items-center justify-between">
          <div>
            <div className="flex items-center gap-1">
              <h1 className="text-xl font-bold">{profile.name}</h1>
              <CheckCircle className="h-4 w-4 fill-primary text-white" />
            </div>
            <p className="text-sm text-textSecondary">@{profile.handle}</p>
          </div>
          <div className="flex gap-2">
            <button className="flex h-10 items-center gap-2 rounded-xl bg-gray-100 px-4 text-sm font-bold transition-all active:scale-95">
              <Edit className="h-4 w-4" />
              Edit
            </button>
            <button className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-white shadow-md transition-all active:scale-95">
              <Plus className="h-5 w-5" />
            </button>
          </div>
        </div>

        {profile.bio && <p className="mt-3 text-sm leading-relaxed font-medium">{profile.bio}</p>}
        {profile.location && <p className="mt-2 text-xs text-textSecondary font-bold">📍 {profile.location}</p>}

        {/* Stats */}
        <div className="mt-6 flex justify-between rounded-2xl bg-gray-50 p-4">
          <div className="text-center flex-1">
            <p className="text-lg font-black">{profile.canSuspendUser ? 'N/A' : '0'}</p>
            <p className="text-[10px] font-bold text-textSecondary uppercase tracking-wider">Followers</p>
          </div>
          <div className="text-center flex-1 border-x border-gray-200">
            <p className="text-lg font-black">{profile.canBanUser ? 'N/A' : '0'}</p>
            <p className="text-[10px] font-bold text-textSecondary uppercase tracking-wider">Following</p>
          </div>
          <div className="text-center flex-1">
            <p className="text-lg font-black">{posts.length}</p>
            <p className="text-[10px] font-bold text-textSecondary uppercase tracking-wider">Posts</p>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="sticky top-16 z-40 bg-white border-b border-gray-100">
        <div className="flex justify-around">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex flex-col items-center gap-1.5 py-3 px-2 transition-all ${
                activeTab === tab.id ? 'text-primary' : 'text-gray-400'
              }`}
            >
              <tab.icon className="h-5 w-5" />
              <div className={`h-1 w-6 rounded-full transition-all ${activeTab === tab.id ? 'bg-primary' : 'bg-transparent'}`} />
            </button>
          ))}
        </div>
      </div>

      {/* Tab Content */}
      <div className="p-4 space-y-6">
        {activeTab === 'GRID' && (
          posts.length > 0 ? (
            posts.map(post => <PostCard key={post.id} post={post} />)
          ) : (
            <div className="py-20 text-center">
              <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-50 text-gray-400">
                <Grid className="h-8 w-8" />
              </div>
              <h3 className="font-bold">No Posts Yet</h3>
              <p className="text-sm text-textSecondary">Share your first photo or update!</p>
            </div>
          )
        )}

        {/* Placeholder for other tabs mirroring Android empty states */}
        {activeTab !== 'GRID' && (
          <div className="py-20 text-center">
            <p className="text-sm text-textSecondary">Coming soon to Web version</p>
          </div>
        )}
      </div>
    </div>
  )
}

export default Profile