import { useState, useEffect } from 'react'
import { SocialService } from '../services/SocialService'
import { PostEntity, StoryEntity } from '../types'
import PostCard from '../components/PostCard'
import StoriesTray from '../components/StoriesTray'

const Home = () => {
  const [posts, setPosts] = useState<PostEntity[]>([])
  const [stories, setStories] = useState<StoryEntity[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const loadData = async () => {
      try {
        const [postsData, storiesData] = await Promise.all([
          SocialService.fetchPosts(),
          SocialService.fetchStories()
        ])
        setPosts(postsData)
        setStories(storiesData)
      } catch (error) {
        console.error('Error loading home data:', error)
      } finally {
        setLoading(false)
      }
    }

    loadData()
  }, [])

  if (loading) {
    return (
      <div className="flex h-60 w-full items-center justify-center">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
      </div>
    )
  }

  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-4">
      <StoriesTray stories={stories} />

      <div className="mt-6 flex flex-col gap-6">
        {posts.length > 0 ? (
          posts.map((post) => (
            <PostCard key={post.id} post={post} />
          ))
        ) : (
          <div className="flex flex-col items-center justify-center rounded-3xl bg-gray-50 p-12 text-center">
            <div className="mb-4 rounded-full bg-white p-4 shadow-sm">
              <span className="text-4xl">📸</span>
            </div>
            <h3 className="text-lg font-bold">No posts yet</h3>
            <p className="text-sm text-textSecondary">Be the first to share something with the community!</p>
          </div>
        )}
      </div>
    </div>
  )
}

export default Home