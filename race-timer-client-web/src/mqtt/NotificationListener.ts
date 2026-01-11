import mqtt, { MqttClient } from 'mqtt';
import { NotificationEvent } from '../models/ApiTypes';

/**
 * Handler function for Start List change notifications.
 */
export type StartListChangeHandler = (event: NotificationEvent) => void;

/**
 * MQTT client for receiving real-time notifications from the repository.
 */
export class NotificationListener {
  private client: MqttClient | null = null;
  private readonly brokerUrl: string;
  private readonly clientId: string;
  private readonly regattaId: string;
  private readonly handlers: StartListChangeHandler[] = [];
  private reconnectAttempts: number = 0;
  private readonly maxReconnectAttempts: number = 10;

  constructor(brokerUrl: string, clientId: string, regattaId: string) {
    this.brokerUrl = brokerUrl;
    this.clientId = clientId;
    this.regattaId = regattaId;
  }

  /**
   * Connect to MQTT broker and subscribe to Start List changes.
   */
  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        this.client = mqtt.connect(this.brokerUrl, {
          clientId: this.clientId,
          clean: true,
          reconnectPeriod: 5000,
          connectTimeout: 10000,
        });

        this.client.on('connect', () => {
          console.log('MQTT connected');
          this.reconnectAttempts = 0;

          // Subscribe to Start List changes for this regatta
          const topic = `regatta/${this.regattaId}/startlist`;
          this.client?.subscribe(topic, { qos: 1 }, (error) => {
            if (error) {
              console.error('Failed to subscribe to MQTT topic', error);
              reject(error);
            } else {
              console.log(`Subscribed to MQTT topic: ${topic}`);
              resolve();
            }
          });
        });

        this.client.on('message', (topic: string, payload: Buffer) => {
          this.handleMessage(topic, payload);
        });

        this.client.on('error', (error) => {
          console.error('MQTT error', error);
          reject(error);
        });

        this.client.on('reconnect', () => {
          this.reconnectAttempts++;
          console.log(`MQTT reconnecting (attempt ${this.reconnectAttempts})`);

          if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('Max MQTT reconnect attempts reached');
            this.disconnect();
          }
        });

        this.client.on('offline', () => {
          console.warn('MQTT client offline');
        });

        this.client.on('close', () => {
          console.log('MQTT connection closed');
        });
      } catch (error) {
        console.error('Failed to create MQTT client', error);
        reject(error);
      }
    });
  }

  /**
   * Disconnect from MQTT broker.
   */
  disconnect(): void {
    if (this.client) {
      this.client.end(true);
      this.client = null;
      console.log('MQTT disconnected');
    }
  }

  /**
   * Check if MQTT client is connected.
   */
  isConnected(): boolean {
    return this.client?.connected ?? false;
  }

  /**
   * Add a handler for Start List change notifications.
   */
  addChangeHandler(handler: StartListChangeHandler): void {
    this.handlers.push(handler);
  }

  /**
   * Remove a handler for Start List change notifications.
   */
  removeChangeHandler(handler: StartListChangeHandler): void {
    const index = this.handlers.indexOf(handler);
    if (index >= 0) {
      this.handlers.splice(index, 1);
    }
  }

  /**
   * Handle incoming MQTT message.
   */
  private handleMessage(topic: string, payload: Buffer): void {
    try {
      const message = payload.toString();
      console.log(`MQTT message received on ${topic}:`, message);

      const event: NotificationEvent = JSON.parse(message);

      // Notify all registered handlers
      this.handlers.forEach(handler => {
        try {
          handler(event);
        } catch (error) {
          console.error('Error in notification handler', error);
        }
      });
    } catch (error) {
      console.error('Failed to parse MQTT message', error);
    }
  }
}
