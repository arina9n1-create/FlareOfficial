import { useState, useEffect } from 'react'
import { supabase } from '../lib/supabase'
import { AppUserEntity, PostEntity } from '../types'
import { Search as SearchIcon, TrendingUp, UserPlus, CheckCircle } from 'lucide-react'

const Search = () => {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<AppUserEntity[]>([])
  const [trendingTags, setTrendingTags] = useState<string[]>([])
  const [explorePosts, setExplorePosts] = useState<PostEntity[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    // Mirroring Android trending logic
    const loadExploreData = async () => {
      const { data: posts } = await supabase.from('posts').select('*').limit(20)
      if (posts) {
        setExplorePosts(posts.filter(p => p.postImageRes || p.storagePath))

        // Basic hashtag extraction from captions
        const tags = posts.flatMap(p => p.caption.match(/#\w+/g) || [])
        const uniqueTags = Array.from(new Set(tags)).slice(0, 6)
        setTrendingTags(uniqueTags)
      }
    }
    loadExploreData()
  }, [])

  useEffect(() => {
    if (!query.trim()) {
      setResults([])
      return
    }

    const searchUsers = async () => {
      setLoading(true)
      const { data } = await supabase
        .from('app_users')
        .select('*')
        .or(`name.ilike.%${query}%,handle.ilike.%${query}%`)
        .limit(10)

      setResults(data || [])
      setLoading(false)
    }

    const timer = setTimeout(searchUsers, 500)
    return () => clearTimeout(timer)
  }, [query])

  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-4 min-h-screen pb-24">
      {/* Search Bar */}
      <div className="relative">
        <SearchIcon className="absolute left-4 top-3.5 h-5 w-5 text-gray-400" />
        <input
          type="text"
          placeholder="Search friends, places, hashtags..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="w-full rounded-full border border-gray-100 bg-white py-3 pl-12 pr-4 outline-none focus:border-primary focus:ring-1 focus:ring-primary shadow-sm"
        />
      </div>

      {/* Trending Tags */}
      {trendingTags.length > 0 && (
        <div className="mt-6 flex gap-2 overflow-x-auto pb-2 scrollbar-hide">
          {trendingTags.map((tag) => (
            <button
              key={tag}
              onClick={() => setQuery(tag)}
              className="flex shrink-0 items-center gap-1.5 rounded-full bg-gray-100 px-4 py-1.5 text-xs font-bold transition-all active:scale-95 hover:bg-gray-200"
            >
              <TrendingUp className="h-3 w-3 text-primary" />
              {tag}
            </button>
          ))}
        </div>
      )}

      {/* Search Results */}
      {query.trim() && (
        <div className="mt-6 space-y-4">
          <h3 className="text-sm font-black uppercase tracking-wider text-textSecondary px-2">Accounts</h3>
          {loading ? (
            <div className="flex justify-center py-4">
              <div className="h-5 w-5 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            </div>
          ) : results.length > 0 ? (
            results.map((user) => (
              <div key={user.uid} className="flex items-center justify-between rounded-2xl bg-white p-3 shadow-sm border border-gray-50 transition-all hover:border-primary/20">
                <div className="flex items-center gap-3">
                  <img
                    src={user.avatarPath || `https://ui-avatars.com/api/?name=${user.name}&background=random`}
                    className="h-12 w-12 rounded-full object-cover"
                    alt=""
                  />
                  <div>
                    <div className="flex items-center gap-1">
                       <h4 className="font-bold text-sm">{user.name}</h4>
                       <CheckCircle className="h-3 w-3 fill-primary text-white" />
                    </div>
                    <p className="text-xs text-textSecondary">@{user.handle}</p>
                  </div>
                </div>
                <button className="rounded-xl bg-primary px-4 py-1.5 text-xs font-black text-white shadow-sm transition-all active:scale-95">Follow</button>
              </div>
            ))
          ) : (
            <p className="px-2 text-sm text-textSecondary">No accounts found for "{query}"</p>
          )}
        </div>
      )}

      {/* Explore Grid */}
      {!query.trim() && (
        <div className="mt-8">
          <h3 className="mb-4 text-lg font-black italic text-primary">Explore Trends</h3>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {explorePosts.map((post) => (
              <div key={post.id} className="aspect-square overflow-hidden rounded-xl bg-gray-100 transition-all hover:opacity-90 cursor-pointer shadow-sm border border-gray-100">
                <img
                  src={post.storagePath || post.postImageRes}
                  className="h-full w-full object-cover"
                  alt=""
                />
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export default Search