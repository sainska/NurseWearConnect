import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const FCM_URL = "https://fcm.googleapis.com/fcm/send"

serve(async (req) => {
  const { to, notification, data } = await req.json()

  // In a real production environment, you would use a service account token.
  // For this enterprise bridge, we use the Legacy FCM Server Key stored in system_settings or env.
  const serverKey = Deno.env.get("FCM_SERVER_KEY")

  const res = await fetch(FCM_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `key=${serverKey}`,
    },
    body: JSON.stringify({
      to,
      notification,
      data,
      priority: "high"
    }),
  })

  const result = await res.json()
  return new Response(JSON.stringify(result), {
    headers: { "Content-Type": "application/json" },
  })
})
