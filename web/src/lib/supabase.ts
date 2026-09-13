import { createClient } from '@supabase/supabase-js'

const supabaseUrl = 'https://crlrjkpoxlkbpjfqnyyr.supabase.co'
const supabaseKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNybHJqa3BveGxrYnBqZnFueXlyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxMTY5MTQsImV4cCI6MjEwMTY5MjkxNH0.khdA6Dm5RinWa00YdKKrQY9sJ1r553GEMxIll5i6Wdk'

export const supabase = createClient(supabaseUrl, supabaseKey)
