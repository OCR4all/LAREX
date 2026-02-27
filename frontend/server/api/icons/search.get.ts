export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig(event)
  const query = getQuery(event)

  const iconifyUrl = config.iconifyApiUrl
  const response = await $fetch(`${iconifyUrl}/search`, { query })
  return response
})
