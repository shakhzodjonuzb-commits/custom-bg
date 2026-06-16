/**
 * Firebase Cloud Function for background removal using remove.bg API
 *
 * Requirements:
 * 1. Initialize npm in functions directory: npm init -y
 * 2. Install axios: npm install axios firebase-functions firebase-admin
 * 3. Set REMOVE_BG_API_KEY env var on Google Cloud / Firebase console
 */

const { onRequest } = require("firebase-functions/v2/https");
const { logger } = require("firebase-functions");
const axios = require("axios");

exports.removeBackground = onRequest({ cors: true, timeoutSeconds: 60 }, async (req, res) => {
  try {
    // 1. Check HTTP request method
    if (req.method !== "POST") {
      res.status(405).json({ error: "Only POST requests are supported" });
      return;
    }

    const { image } = req.body;
    if (!image) {
      res.status(400).json({ error: "Image payload in Base64 is required" });
      return;
    }

    // 2. Fetch server-side API Key
    // remove.bg API key should be loaded securely from secret manager or environment variable
    const apiKey = process.env.REMOVE_BG_API_KEY;
    if (!apiKey) {
      logger.error("REMOVE_BG_API_KEY environment variable is not configured");
      res.status(500).json({ error: "Server configuration error. API key is missing." });
      return;
    }

    logger.info("Processing background removal request...");

    // 3. Prepare Multipart Form Data for remove.bg API
    // We construct a FormData compatible multipart payload to send the base64 string
    const FormData = require("form-data");
    const formData = new FormData();
    formData.append("image_file_b64", image);
    formData.append("size", "auto");

    // 4. Request remove.bg
    const response = await axios.post("https://api.remove.bg/v1.0/removebg", formData, {
      headers: {
        ...formData.getHeaders(),
        "X-Api-Key": apiKey,
      },
      responseType: "arraybuffer", // Receive transparent PNG as binary array buffer
    });

    // 5. Convert binary response to Base64 and return
    const processedBase64 = Buffer.from(response.data, "binary").toString("base64");
    
    logger.info("Background removed successfully!");
    res.status(200).json({
      image: processedBase64,
      format: "png"
    });

  } catch (error) {
    logger.error("Error in removeBackground Cloud Function:", error.message);
    if (error.response && error.response.data) {
      // Decode remove.bg API specific errors if available
      try {
        const errorText = Buffer.from(error.response.data).toString("utf-8");
        logger.error("remove.bg API response error:", errorText);
        res.status(error.response.status || 500).json({
          error: "remove.bg API failure",
          details: errorText
        });
        return;
      } catch (e) {
        // ignore decoding errors
      }
    }
    res.status(500).json({ error: "Failed to process background removal: " + error.message });
  }
});
