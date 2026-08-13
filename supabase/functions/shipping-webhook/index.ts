import { serve } from "https://deno.land/std@0.203.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.38.0'

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')

serve(async (req) => {
  try {
    if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) throw new Error('Supabase configuration missing')

    const { tracking_number, status, carrier } = await req.json()

    if (!tracking_number || !status) {
      throw new Error('Missing tracking_number or status')
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

    // 1. Find the order with this tracking number
    // We use the 'tracking_number' column added in migration 20260816010000
    const { data: order, error: orderError } = await supabase
      .from('orders')
      .select('id, user_id')
      .eq('tracking_number', tracking_number)
      .single()

    if (orderError || !order) {
        console.log(`Order not found for tracking number: ${tracking_number}`)
        return new Response(JSON.stringify({ error: "Order not found" }), { status: 404 })
    }

    // 2. Update status
    await supabase
      .from('orders')
      .update({
        status: status.toLowerCase(),
        updated_at: new Date().toISOString()
      })
      .eq('id', order.id)

    // 3. Log to Status History
    await supabase
      .from('order_status_history')
      .insert({
        order_id: order.id,
        status: status.toLowerCase(),
        notes: `Shipping status updated to ${status} via ${carrier || 'Partner'}`
      })

    // 4. Notify User
    await supabase
      .from('notifications')
      .insert({
        user_id: order.user_id,
        title: "Shipping Update",
        content: `Your package with tracking #${tracking_number} (${carrier || 'Carrier'}) is now ${status}.`,
        type: "SHIPPING"
      })

    return new Response(JSON.stringify({ message: "Update processed successfully" }), {
      headers: { "Content-Type": "application/json" },
      status: 200
    })
  } catch (error: any) {
    console.error('Shipping Webhook Error:', error.message)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { "Content-Type": "application/json" },
      status: 400
    })
  }
})
