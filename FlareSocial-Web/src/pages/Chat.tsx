import { useState, useEffect, useRef } from 'react'
import { supabase } from '../lib/supabase'
import { ChatMessageEntity, AppUserEntity } from '../types'
import { Camera, Phone, Video, Info, Send, Smile, Image as ImageIcon, Mic, ChevronLeft } from 'lucide-react'
import { formatDistanceToNow } from 'date-fns'

const Chat = () => {
  const [messages, setMessages] = useState<ChatMessageEntity[]>([])
  const [inputText, setInputText] = useState('')
  const [profile, setProfile] = useState<AppUserEntity | null>(null)
  const [activeRoom, setActiveRoom] = useState<string | null>(null)
  const [rooms, setAvailableRooms] = useState<any[]>([])
  const messagesEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const loadRooms = async () => {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) return

      const { data: profileData } = await supabase.from('app_users').select('*').eq('uid', user.id).single()
      setProfile(profileData)

      // Fetch conversation rooms mirroring Android logic
      const { data: roomsData } = await supabase.from('chat_messages')
        .select('roomId, senderName, senderHandle, senderAvatar')
        .order('timestamp', { ascending: false })

      // Basic unique room extraction for demo mirroring Android UI
      const uniqueRooms = Array.from(new Set(roomsData?.map(r => r.roomId)))
        .map(id => roomsData?.find(r => r.roomId === id))

      setAvailableRooms(uniqueRooms || [])
    }

    loadRooms()
  }, [])

  useEffect(() => {
    if (!activeRoom) return

    const fetchMessages = async () => {
      const { data } = await supabase
        .from('chat_messages')
        .select('*')
        .eq('roomId', activeRoom)
        .order('timestamp', { ascending: true })

      setMessages(data || [])
    }

    fetchMessages()

    // Realtime subscription mirroring Android
    const channel = supabase
      .channel(`room_${activeRoom}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'chat_messages', filter: `roomId=eq.${activeRoom}` },
      (payload) => {
        setMessages(prev => [...prev, payload.new as ChatMessageEntity])
      })
      .subscribe()

    return () => {
      supabase.removeChannel(channel)
    }
  }, [activeRoom])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!inputText.trim() || !profile || !activeRoom) return

    const newMessage = {
      roomId: activeRoom,
      senderName: profile.name,
      senderHandle: profile.handle,
      senderAvatar: profile.avatarType,
      messageText: inputText,
      timestamp: Date.now(),
      isFromMe: true,
      mediaType: 'text'
    }

    const { error } = await supabase.from('chat_messages').insert([newMessage])
    if (!error) setInputText('')
  }

  if (!activeRoom) {
    return (
      <div className="mx-auto w-full max-w-2xl bg-white min-h-screen pb-20">
        <div className="sticky top-16 z-40 flex items-center justify-between bg-white px-4 py-4 border-b border-gray-100">
          <h1 className="text-xl font-bold italic text-primary">Messages</h1>
          <div className="flex gap-4">
            <Video className="h-6 w-6 text-gray-700" />
            <span className="font-bold text-primary">New</span>
          </div>
        </div>

        <div className="p-4">
          <div className="mb-6 flex gap-4 overflow-x-auto pb-2">
            <div className="flex flex-col items-center gap-1 shrink-0">
              <div className="h-16 w-16 rounded-full border-2 border-dashed border-gray-300 flex items-center justify-center bg-gray-50">
                <Plus className="h-6 w-6 text-gray-400" />
              </div>
              <span className="text-[10px] text-textSecondary font-bold">Your note</span>
            </div>
          </div>

          <div className="space-y-4">
            {rooms.map((room) => (
              <div
                key={room.roomId}
                onClick={() => setActiveRoom(room.roomId)}
                className="flex items-center gap-4 p-2 rounded-2xl hover:bg-gray-50 transition-all cursor-pointer active:scale-98"
              >
                <div className="h-14 w-14 rounded-full border-2 border-primary/20 p-0.5">
                  <img src={`https://ui-avatars.com/api/?name=${room.senderName}&background=random`} className="h-full w-full rounded-full object-cover" alt="" />
                </div>
                <div className="flex-1 min-w-0">
                  <h4 className="font-bold text-sm truncate">{room.senderName}</h4>
                  <p className="text-xs text-textSecondary truncate">Active now</p>
                </div>
                <Camera className="h-5 w-5 text-gray-400" />
              </div>
            ))}
          </div>
        </div>
      </div>
    )
  }

  const currentChatPeer = rooms.find(r => r.roomId === activeRoom)

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-white max-w-4xl mx-auto shadow-2xl">
      {/* Chat Header */}
      <div className="flex h-16 items-center justify-between border-b border-gray-100 px-4">
        <div className="flex items-center gap-3">
          <button onClick={() => setActiveRoom(null)} className="rounded-full p-1 hover:bg-gray-100">
            <ChevronLeft className="h-6 w-6" />
          </button>
          <div className="h-10 w-10 rounded-full bg-gray-100">
             <img src={`https://ui-avatars.com/api/?name=${currentChatPeer?.senderName}&background=random`} className="h-full w-full rounded-full" alt="" />
          </div>
          <div>
            <h4 className="text-sm font-bold">{currentChatPeer?.senderName || 'Global Chat'}</h4>
            <p className="text-[10px] text-green-500 font-bold">Active now</p>
          </div>
        </div>
        <div className="flex items-center gap-4">
          <Phone className="h-5 w-5 text-gray-700" />
          <Video className="h-6 w-6 text-gray-700" />
          <Info className="h-5 w-5 text-gray-700" />
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        <div className="flex flex-col items-center py-10 text-center">
           <div className="h-24 w-24 rounded-full border-4 border-primary/10 p-1 mb-4">
             <img src={`https://ui-avatars.com/api/?name=${currentChatPeer?.senderName}&background=random`} className="h-full w-full rounded-full" alt="" />
           </div>
           <h3 className="text-lg font-bold">{currentChatPeer?.senderName}</h3>
           <p className="text-xs text-textSecondary italic">You follow each other on FlareOfficial</p>
           <button className="mt-4 rounded-xl bg-gray-100 px-4 py-2 text-xs font-bold transition-all active:scale-95">View Profile</button>
        </div>

        {messages.map((msg) => {
          const isMe = msg.senderHandle === profile?.handle
          return (
            <div key={msg.id} className={`flex ${isMe ? 'justify-end' : 'justify-start'}`}>
              <div className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-sm shadow-sm ${
                isMe ? 'bg-gradient-to-br from-primary to-secondary text-white' : 'bg-gray-100 text-gray-800'
              }`}>
                {msg.messageText}
                <div className={`mt-1 text-[8px] opacity-70 ${isMe ? 'text-right' : 'text-left'}`}>
                  {msg.timestamp ? formatDistanceToNow(new Date(msg.timestamp)) : ''}
                </div>
              </div>
            </div>
          )
        })}
        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <div className="p-4">
        <div className="flex items-center gap-2 rounded-3xl bg-gray-100 px-4 py-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-white">
            <Camera className="h-4 w-4" />
          </div>
          <form onSubmit={handleSendMessage} className="flex-1">
            <input
              type="text"
              placeholder="Message..."
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              className="w-full bg-transparent py-2 text-sm outline-none"
            />
          </form>
          {inputText.trim() ? (
            <button onClick={handleSendMessage} className="text-sm font-bold text-primary px-2">Send</button>
          ) : (
            <div className="flex items-center gap-3 text-gray-500">
              <Mic className="h-5 w-5" />
              <ImageIcon className="h-5 w-5" />
              <Smile className="h-5 w-5" />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

const Plus = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M5 12h14"/><path d="M12 5v14"/></svg>
)

export default Chat