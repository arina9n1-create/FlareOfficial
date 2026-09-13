import { useState, useEffect, useRef } from 'react'
import { supabase } from '../lib/supabase'
import { ReelEntity } from '../types'
import { Heart, MessageCircle, Repeat, Bookmark, MoreVertical, Music, Share2, Plus, Volume2, VolumeX } from 'lucide-react'

const Reels = () => {
  const [reels, setReels] = useState<ReelEntity[]>([])
  const [activeReelIndex, setActiveReelIndex] = useState(0)
  const [isMuted, setIsMuted] = useState(false)
  const [loading, setLoading] = useState(true)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const fetchReels = async () => {
      const { data } = await supabase
        .from('reels')
        .select('*')
        .order('timestamp', { ascending: false })

      setReels(data || [])
      setLoading(false)
    }

    fetchReels()
  }, [])

  const handleScroll = () => {
    if (!containerRef.current) return
    const index = Math.round(containerRef.current.scrollTop / containerRef.current.clientHeight)
    setActiveReelIndex(index)
  }

  if (loading) {
    return (
      <div className="flex h-screen w-full items-center justify-center bg-black">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"></div>
      </div>
    )
  }

  return (
    <div className="fixed inset-0 z-50 bg-black max-w-lg mx-auto overflow-hidden">
      <div
        ref={containerRef}
        onScroll={handleScroll}
        className="h-full overflow-y-scroll snap-y snap-mandatory scrollbar-hide"
      >
        {reels.map((reel, index) => (
          <div key={reel.id} className="relative h-full w-full snap-start bg-black flex flex-col items-center justify-center">
             {/* Video Background */}
             <div className="absolute inset-0 flex items-center justify-center overflow-hidden">
                <img
                  src={reel.storagePath || reel.imageRes || `https://picsum.photos/seed/${reel.id}/400/800`}
                  className="h-full w-full object-cover opacity-60 blur-sm"
                  alt=""
                />
                <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-black/40" />
                <div className="relative z-10 max-h-[85vh] w-full flex items-center justify-center">
                   {/* In a real web app, use <video> here. Mirroring Android placeholder */}
                   <div className="aspect-[9/16] h-full bg-gray-900 rounded-2xl flex items-center justify-center overflow-hidden border border-white/10">
                      <img
                        src={reel.storagePath || reel.imageRes}
                        className="h-full w-full object-contain"
                        alt="Reel content"
                      />
                      <div className="absolute inset-0 flex items-center justify-center">
                         <PlayCircle className="h-20 w-20 text-white/40" />
                      </div>
                   </div>
                </div>
             </div>

             {/* Right Sidebar Actions */}
             <div className="absolute right-4 bottom-32 z-20 flex flex-col items-center gap-6">
                <div className="flex flex-col items-center gap-1 group">
                   <button className="h-12 w-12 rounded-full bg-white/10 backdrop-blur-md flex items-center justify-center transition-all active:scale-90 group-hover:bg-white/20">
                      <Heart className={`h-7 w-7 ${reel.isLiked ? 'fill-red-500 text-red-500' : 'text-white'}`} />
                   </button>
                   <span className="text-xs font-bold text-white drop-shadow-md">{reel.likesCount}</span>
                </div>

                <div className="flex flex-col items-center gap-1 group">
                   <button className="h-12 w-12 rounded-full bg-white/10 backdrop-blur-md flex items-center justify-center transition-all active:scale-90 group-hover:bg-white/20">
                      <MessageCircle className="h-7 w-7 text-white" />
                   </button>
                   <span className="text-xs font-bold text-white drop-shadow-md">{reel.commentsCount}</span>
                </div>

                <div className="flex flex-col items-center gap-1 group">
                   <button className="h-12 w-12 rounded-full bg-white/10 backdrop-blur-md flex items-center justify-center transition-all active:scale-90 group-hover:bg-white/20">
                      <Repeat className="h-7 w-7 text-white" />
                   </button>
                   <span className="text-xs font-bold text-white drop-shadow-md">{reel.sharesCount}</span>
                </div>

                <button className="h-12 w-12 rounded-full bg-white/10 backdrop-blur-md flex items-center justify-center transition-all active:scale-90 hover:bg-white/20">
                   <Bookmark className={`h-7 w-7 ${reel.isSaved ? 'fill-yellow-500 text-yellow-500' : 'text-white'}`} />
                </button>

                <div className="h-10 w-10 rounded-full border-2 border-white/60 p-0.5 animate-spin-slow">
                   <img src={`https://ui-avatars.com/api/?name=${reel.author}&background=random`} className="h-full w-full rounded-full" alt="" />
                </div>
             </div>

             {/* Bottom Details */}
             <div className="absolute left-4 right-20 bottom-24 z-20 space-y-3">
                <div className="flex items-center gap-3">
                   <div className="h-10 w-10 rounded-full border-2 border-primary overflow-hidden">
                      <img src={`https://ui-avatars.com/api/?name=${reel.author}&background=random`} className="h-full w-full" alt="" />
                   </div>
                   <h4 className="font-bold text-white text-base">@{reel.handle}</h4>
                   <button className="px-3 py-1 rounded-lg border border-white/60 text-[10px] font-black text-white uppercase tracking-tighter hover:bg-white/10">Follow</button>
                </div>

                <p className="text-sm text-white line-clamp-2 leading-relaxed font-medium drop-shadow-md">{reel.caption}</p>

                <div className="flex items-center gap-2 bg-black/40 backdrop-blur-md rounded-full px-3 py-1.5 w-fit border border-white/10">
                   <Music className="h-3.5 w-3.5 text-white" />
                   <span className="text-[10px] text-white font-bold truncate max-w-[150px]">{reel.music}</span>
                </div>
             </div>

             {/* Top Overlay UI */}
             <div className="absolute top-0 left-0 right-0 p-4 z-30 flex justify-between items-center">
                <h2 className="text-xl font-black text-white italic tracking-widest">REELS</h2>
                <div className="flex gap-4">
                   <button onClick={() => setIsMuted(!isMuted)} className="text-white/80 hover:text-white">
                      {isMuted ? <VolumeX /> : <Volume2 />}
                   </button>
                   <MoreVertical className="text-white/80" />
                </div>
             </div>
          </div>
        ))}
      </div>
    </div>
  )
}

const PlayCircle = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round" className={className}><circle cx="12" cy="12" r="10"/><polygon points="10 8 16 12 10 16 10 8"/></svg>
)

export default Reels