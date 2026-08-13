/**
 * Simulates M-Pesa STK Push Payment Response Logic
 * @param {string} checkoutRequestId
 * @returns {Promise<Object>}
 */
export const simulateStkPushStatus = async (checkoutRequestId) => {
    return new Promise((resolve) => {
      // Wait for a simulated user to enter PIN
      setTimeout(() => {
        const success = Math.random() > 0.2; // 80% success rate simulation
        if (success) {
          resolve({
            status: 'COMPLETED',
            checkoutRequestId,
            resultCode: 0,
            resultDesc: 'The service was accepted successfully',
            transactionId: `MWC-${Math.random().toString(36).substr(2, 9).toUpperCase()}`
          });
        } else {
          resolve({
            status: 'CANCELLED',
            checkoutRequestId,
            resultCode: 1032,
            resultDesc: 'Request cancelled by user'
          });
        }
      }, 5000);
    });
  };

  /**
   * Formats a phone number to M-Pesa format (2547XXXXXXXX)
   * @param {string} phoneNumber
   * @returns {string}
   */
  export const formatMpesaPhoneNumber = (phoneNumber) => {
    let cleaned = phoneNumber.replace(/\D/g, '');
    if (cleaned.startsWith('0')) {
      cleaned = '254' + cleaned.substring(1);
    } else if (cleaned.startsWith('+')) {
      cleaned = cleaned.substring(1);
    } else if (!cleaned.startsWith('254')) {
      cleaned = '254' + cleaned;
    }
    return cleaned;
  };

  /**
   * Generates a unique tracking number for new orders
   * @returns {string}
   */
  export const generateTrackingNumber = () => {
    const prefix = 'NW';
    const timestamp = Date.now().toString().slice(-6);
    const random = Math.floor(Math.random() * 1000).toString().padStart(3, '0');
    return `${prefix}${timestamp}${random}`;
  };
