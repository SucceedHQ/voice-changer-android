export class AudioEngine {
    constructor() {
        this.context = null;
        this.processor = null;
        this.stream = null;
        this.source = null;
    }

    async init() {
        this.context = new (window.AudioContext || window.webkitAudioContext)();
        this.stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        this.source = this.context.createMediaStreamSource(this.stream);

        await this.context.audioWorklet.addModule('/worklets/voice-processor.js');
        this.processor = new AudioWorkletNode(this.context, 'voice-processor');

        this.source.connect(this.processor);
        this.processor.connect(this.context.destination);
    }

    setPersona(persona) {
        if (!this.processor) return;

        let pitch = 1.0;
        let formant = 1.0;

        if (persona === 'Male') {
            pitch = 0.75;
            formant = 0.85;
        } else if (persona === 'Female') {
            pitch = 1.4;
            formant = 1.15;
        }

        this.processor.port.postMessage({ type: 'SET_PITCH', value: pitch });
        this.processor.port.postMessage({ type: 'SET_FORMANT', value: formant });
    }

    async stop() {
        if (this.context) await this.context.close();
        if (this.stream) this.stream.getTracks().forEach(t => t.stop());
    }
}
