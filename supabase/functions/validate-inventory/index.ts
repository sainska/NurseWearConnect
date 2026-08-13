import { serve } from "https://deno.land/std@0.203.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.38.0'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const { orderId, order_id } = await req.json()
    const id = orderId || order_id

    // If it's just a pre-order check, we don't have a real order ID yet
    if (id === 'temp_validation') {
        return new Response(JSON.stringify({ valid: true, message: "Inventory verified (Pre-order check)." }), {
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            status: 200,
        })
    }

    // 1. Fetch order items and their current product stock
    const { data: items, error: itemsError } = await supabaseClient
      .from('order_items')
      .select('product_id, quantity, products(name, stock_count)')
      .eq('order_id', id)

    if (itemsError) throw itemsError

    const insufficientStock = items.filter((item: any) => item.products.stock_count < item.quantity)

    if (insufficientStock.length > 0) {
      const details = insufficientStock.map((item: any) => `${item.products.name} (Available: ${item.products.stock_count}, Requested: ${item.quantity})`).join(', ')

      // Update order status to cancelled or failed if stock is invalid
      await supabaseClient
        .from('orders')
        .update({ status: 'cancelled', notes: `Inventory validation failed: ${details}` })
        .eq('id', orderId)

      return new Response(JSON.stringify({
        valid: false,
        message: `Some items in your order are no longer available in the requested quantities: ${details}`
      }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200,
      })
    }

    return new Response(JSON.stringify({ valid: true, message: "Inventory verified." }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    })

  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
