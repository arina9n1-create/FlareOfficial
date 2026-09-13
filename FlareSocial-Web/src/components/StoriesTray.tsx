import { Plus } from 'lucide-react'
import { StoryEntity } from '../types'

interface StoriesTrayProps {
  stories: StoryEntity[]
}

const StoriesTray = ({ stories }: StoriesTrayProps) => {
  return (
    <div className="flex gap-4 overflow-x-auto pb-2 scrollbar-hide">
      {/* Add Story Button */}
      <div className="flex flex-col items-center gap-1 shrink-0 cursor-pointer">
        <div className="relative flex h-[150px] w-[100px] items-center justify-center rounded-2xl bg-gray-100 border border-dashed border-gray-300 transition-all hover:bg-gray-200">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-white shadow-lg">
            <Plus className="h-5 w-5" />
          </div>
          <span className="absolute bottom-2 text-[10px] font-bold">Add Story</span>
        </div>
      </div>

      {/* Story Items */}
      {stories.map((story) => (
        <div key={story.id} className="flex flex-col items-center gap-1 shrink-0 cursor-pointer group">
          <div className="relative h-[150px] w-[100px] overflow-hidden rounded-2xl border border-gray-100 shadow-sm transition-all group-hover:scale-[1.02]">
            <img
              src={story.storagePath || `https://picsum.photos/seed/${story.id}/200/300`}
              alt={story.username}
              className="h-full w-full object-cover"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" />
            <div className="absolute top-2 left-2 h-8 w-8 overflow-hidden rounded-full border-2 border-primary bg-white">
              <img src={`https://ui-avatars.com/api/?name=${story.username}&background=random`} alt={story.username} />
            </div>
            <span className="absolute bottom-2 left-2 right-2 truncate text-[10px] font-bold text-white">
              {story.username}
            </span>
          </div>
        </div>
      ))}
    </div>
  )
}

export default StoriesTray