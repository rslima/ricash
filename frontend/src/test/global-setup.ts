// Runs once in the main Vitest process before any worker starts, so every
// test file inherits the zone. America/Sao_Paulo is UTC-3 with no DST.
export default function setup() {
  process.env.TZ = "America/Sao_Paulo"
}
