import { isNativePlatform, getPlatform } from "./capacitor"

export async function initNative(): Promise<void> {
  if (!isNativePlatform()) return

  // Configure status bar
  const { StatusBar, Style } = await import("@capacitor/status-bar")

  if (getPlatform() === "ios") {
    await StatusBar.setStyle({ style: Style.Light })
  } else {
    await StatusBar.setStyle({ style: Style.Light })
    await StatusBar.setBackgroundColor({ color: "#ffffff" })
  }

  // Hide splash screen after app is ready
  const { SplashScreen } = await import("@capacitor/splash-screen")
  await SplashScreen.hide()
}
