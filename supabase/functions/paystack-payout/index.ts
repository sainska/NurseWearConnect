import { serve } from "https://deno.land/std@0.203.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.38.0'

const PAYSTACK_SECRET_KEY = Deno.env.get('PAYSTACK_SECRET_KEY')
const SUPABASE_URL = Deno.env.get('SUPABASE_URL')
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    if (!PAYSTACK_SECRET_KEY) throw new Error('PAYSTACK_SECRET_KEY is not configured')
    if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) throw new Error('Supabase configuration missing')

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)
    const { payoutId } = await req.json()

    if (!payoutId) {
      throw new Error('Missing payoutId')
    }

    // 1. Fetch payout details
    const { data: payout, error: payoutError } = await supabase
      .from('payouts')
      .select('*, profiles!vendor_id(*)')
      .eq('id', payoutId)
      .maybeSingle()

    if (payoutError || !payout) {
      throw new Error('Payout record not found')
    }

    if (payout.status === 'paid') {
      return new Response(JSON.stringify({ message: 'Payout already processed' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200,
      })
    }

    const vendor = payout.profiles
    const targetAccount = payout.account_number || vendor.bank_account_number || vendor.phone_number

    // Determine if it's Mobile Money (M-Pesa) or Bank
    const isMobileMoney = payout.withdrawal_method === 'mpesa' || (!vendor.bank_code && targetAccount?.length >= 9)

    let recipientCode = isMobileMoney ? vendor.paystack_mpesa_recipient_code : vendor.paystack_recipient_code

    // 2. Create Paystack recipient if not exists
    if (!recipientCode) {
      if (!targetAccount) {
          throw new Error('Vendor payment details missing in profile or payout request.')
      }

      console.log(`Creating Paystack recipient for Vendor: ${vendor.id} (Mobile: ${isMobileMoney})`)

      const recipientBody: any = {
        type: isMobileMoney ? "mobile_money" : "nuban",
        name: vendor.full_name,
        account_number: targetAccount,
        bank_code: isMobileMoney ? "MPESA" : vendor.bank_code,
        currency: "KES",
      }

      const recipientResponse = await fetch('https://api.paystack.co/transferrecipient', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${PAYSTACK_SECRET_KEY}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(recipientBody),
      })

      const recipientData = await recipientResponse.json()
      if (!recipientData.status) {
        throw new Error(`Failed to create Paystack recipient: ${recipientData.message}`)
      }

      recipientCode = recipientData.data.recipient_code

      // Update vendor profile with recipient code
      const updateField = isMobileMoney ? 'paystack_mpesa_recipient_code' : 'paystack_recipient_code'
      await supabase
        .from('profiles')
        .update({ [updateField]: recipientCode })
        .eq('id', vendor.id)
    }

    // 3. Initiate Transfer
    console.log(`Initiating Paystack transfer for Payout: ${payoutId} to ${recipientCode}`)

    const transferResponse = await fetch('https://api.paystack.co/transfer', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${PAYSTACK_SECRET_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        source: "balance",
        amount: Math.round(payout.amount * 100), // Convert KES to cents/sub-units
        recipient: recipientCode,
        reason: `Payout for ${vendor.business_name || vendor.full_name}`,
        reference: `payout_${payout.id}`,
        metadata: {
          payout_id: payout.id
        }
      }),
    })

    const transferData = await transferResponse.json()
    if (!transferData.status) {
      throw new Error(`Paystack transfer failed: ${transferData.message}`)
    }

    // 4. Update payout record
    await supabase
      .from('payouts')
      .update({
        status: 'paid',
        paid_at: new Date().toISOString(),
        reference: transferData.data.reference
      })
      .eq('id', payout.id)

    return new Response(JSON.stringify({ status: 'success', data: transferData.data }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    })

  } catch (error: any) {
    console.error('Paystack Payout Error:', error.message)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
