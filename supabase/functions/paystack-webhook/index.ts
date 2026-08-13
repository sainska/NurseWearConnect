import { serve } from "https://deno.land/std@0.203.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.38.0'

const PAYSTACK_SECRET_KEY = Deno.env.get('PAYSTACK_SECRET_KEY')
const SUPABASE_URL = Deno.env.get('SUPABASE_URL')
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

/**
 * Robust HMAC-SHA512 verification for Paystack webhooks
 */
async function verifyPaystackSignature(signature: string, body: string, secret: string): Promise<boolean> {
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey(
        "raw",
        encoder.encode(secret),
        { name: "HMAC", hash: "SHA-512" },
        false,
        ["sign"]
    );
    const signatureBuffer = await crypto.subtle.sign("HMAC", key, encoder.encode(body));
    const expectedSignature = Array.from(new Uint8Array(signatureBuffer))
        .map(b => b.toString(16).padStart(2, "0"))
        .join("");
    return signature === expectedSignature;
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const signature = req.headers.get('x-paystack-signature')
    if (!signature || !PAYSTACK_SECRET_KEY) {
      return new Response('Unauthorized', { status: 401 })
    }

    const body = await req.text()
    const isValid = await verifyPaystackSignature(signature, body, PAYSTACK_SECRET_KEY)

    if (!isValid) {
      console.error('Webhook Signature Mismatch')
      return new Response('Invalid signature', { status: 401 })
    }

    const event = JSON.parse(body)
    if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
        throw new Error('Supabase configuration missing')
    }
    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

    console.log(`Processing Webhook Event: ${event.event}, Ref: ${event.data?.reference}`)

    // Handle different event types
    switch (event.event) {
      case 'charge.success': {
        const tx = event.data
        const orderId = tx.metadata?.order_id
        const userId = tx.metadata?.user_id
        const type = tx.metadata?.type
        const topupId = tx.metadata?.topup_id
        const reference = tx.reference
        const paidAmount = tx.amount / 100

        const isWalletTopup = type === 'wallet_topup' || !!topupId || (orderId && orderId.toString().startsWith('topup_'));

        if (isWalletTopup && userId) {
            console.log(`Processing Webhook Wallet Top-up for User: ${userId}, Amount: ${paidAmount}`)
            await supabase.rpc('top_up_wallet', {
                p_user_id: userId,
                p_amount: paidAmount,
                p_reference: reference,
                p_description: 'Paystack Wallet Top-up (Webhook)'
            })
            // Update payments table for history
            await supabase.from('payments').update({ status: 'completed', provider_response: tx }).eq('transaction_id', reference)

        } else {
          // Atomic Update via RPC (Handles both existing and deferred)
          const isValidUuid = (id: any) => id && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id.toString());

          const { error: rpcError } = await supabase.rpc('finalize_successful_payment', {
            p_order_id: isValidUuid(orderId) ? orderId : null,
            p_transaction_id: reference,
            p_amount: paidAmount,
            p_method: tx.channel || 'paystack',
            p_response: tx,
            p_user_id: userId
          })

          if (rpcError) {
            console.error(`Webhook RPC Error for Ref ${reference}:`, rpcError.message)
          } else {
            console.log(`Successfully finalized Ref ${reference} via Webhook (Order: ${orderId || 'Deferred'})`)
          }
        }
        break
      }

      case 'charge.failed': {
          console.warn(`Paystack Charge Failed: ${event.data?.reference}, Reason: ${event.data?.gateway_response}`);
          // Update payment record
          await supabase.from('payments')
            .update({ status: 'failed', provider_response: event.data })
            .eq('transaction_id', event.data?.reference)
          break
      }

      case 'transfer.success': {
        const transfer = event.data
        const payoutId = transfer.metadata?.payout_id

        if (payoutId) {
          await supabase
            .from('payouts')
            .update({
              status: 'paid',
              paid_at: new Date().toISOString(),
              reference_number: transfer.transfer_code
            })
            .eq('id', payoutId)
          console.log(`Payout ${payoutId} marked as paid via Webhook`)
        }
        break
      }

      case 'transfer.failed': {
        const transfer = event.data
        const payoutId = transfer.metadata?.payout_id

        if (payoutId) {
          await supabase
            .from('payouts')
            .update({ status: 'failed', notes: `Failed: ${transfer.reason}` })
            .eq('id', payoutId)
          console.log(`Payout ${payoutId} marked as failed via Webhook`)
        }
        break
      }
    }

    return new Response(JSON.stringify({ status: 'success' }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    })

  } catch (error: any) {
    console.error('Webhook Error:', error.message)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
