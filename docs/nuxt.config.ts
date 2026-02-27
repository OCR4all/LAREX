export default defineNuxtConfig({
    modules: ["nitro-cloudflare-dev"],
    css: ['~/assets/css/main.css'],

    nitro: {
        preset: "cloudflare_module",

        cloudflare: {
            deployConfig: true,
            nodeCompat: true
        }
    }
})