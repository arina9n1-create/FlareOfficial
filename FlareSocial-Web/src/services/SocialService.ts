import { supabase } from '../lib/supabase'
import { PostEntity, StoryEntity, AppUserEntity } from '../types'

export const SocialService = {
  async fetchPosts(): Promise<PostEntity[]> {
    const { data, error } = await supabase
      .from('posts')
      .select('*')
      .order('timestamp', { ascending: false })

    if (error) throw error
    return data || []
  },

  async fetchStories(): Promise<StoryEntity[]> {
    const { data, error } = await supabase
      .from('stories')
      .select('*')
      .order('timestamp', { ascending: false })

    if (error) throw error
    return data || []
  },

  async getUserProfile(uid: string): Promise<AppUserEntity | null> {
    const { data, error } = await supabase
      .from('app_users')
      .select('*')
      .eq('uid', uid)
      .single()

    if (error) return null
    return data
  },

  async toggleLike(postId: number, userId: string, isLiked: boolean) {
    // In a real implementation, this would update a 'likes' table and increment 'likesCount'
    // Following the Android logic of Supabase RPCs or direct updates
    const { error } = await supabase.rpc(isLiked ? 'unlike_post' : 'like_post', {
      p_post_id: postId,
      p_user_id: userId
    })
    return !error
  }
}
