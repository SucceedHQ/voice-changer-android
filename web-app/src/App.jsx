import React, { useState, useEffect, useRef } from 'react';
import { AudioEngine } from './audio-engine';
import './index.css';

const App = () => {
  const [isRecording, setIsRecording] = useState(false);
  const [persona, setPersona] = useState('Neutral');
  const [waveformData, setWaveformData] = useState(new Array(30).fill(10));
  const engineRef = useRef(null);

  useEffect(() => {
    engineRef.current = new AudioEngine();
    return () => engineRef.current?.stop();
  }, []);

  const toggleRecording = async () => {
    if (isRecording) {
      await engineRef.current.stop();
      setIsRecording(false);
    } else {
      try {
        await engineRef.current.init();
        engineRef.current.setPersona(persona);
        setIsRecording(true);
        startVisualization();
      } catch (err) {
        alert('Microphone access denied. Please allow microphone access.');
        console.error(err);
      }
    }
  };

  const startVisualization = () => {
    const updateWave = () => {
      if (!isRecording) return;
      setWaveformData(prev => prev.map(() => Math.random() * 80 + 20));
      requestAnimationFrame(updateWave);
    };
    updateWave();
  };

  const handlePersonaChange = (p) => {
    setPersona(p);
    engineRef.current?.setPersona(p);
  };

  return (
    <div className="app-container">
      <div className="header">
        <h1 style={{ fontSize: '2rem', fontWeight: 800, margin: 0 }}>AI VOICE</h1>
        <p style={{ color: 'rgba(255,255,255,0.5)', margin: 0 }}>Studio Edition</p>
      </div>

      <div className="glass-card">
        <div className="waveform-container">
          {waveformData.map((h, i) => (
            <div
              key={i}
              className="wave-bar"
              style={{
                height: isRecording ? `${h}px` : '10px',
                opacity: (i / 30) + 0.2,
                background: `linear-gradient(to top, var(--accent), var(--primary))`
              }}
            />
          ))}
        </div>

        <div style={{ display: 'flex', justifyContent: 'center', gap: '1rem', marginTop: '2rem' }}>
          {['Male', 'Neutral', 'Female'].map((p) => (
            <button
              key={p}
              onClick={() => handlePersonaChange(p)}
              style={{
                padding: '0.8rem 1.5rem',
                borderRadius: '16px',
                border: 'none',
                background: persona === p ? 'var(--primary)' : 'rgba(255,255,255,0.1)',
                color: 'white',
                cursor: 'pointer',
                transition: 'all 0.3s',
                fontWeight: 600
              }}
            >
              {p}
            </button>
          ))}
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '1.5rem', marginBottom: '2rem' }}>
        <button
          onClick={toggleRecording}
          className="record-trigger"
          style={{
            width: '80px',
            height: '80px',
            borderRadius: '50%',
            background: isRecording ? '#ff4b2b' : 'var(--primary)',
            border: 'none',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: `0 0 30px ${isRecording ? 'rgba(255,75,43,0.4)' : 'rgba(157,80,187,0.4)'}`,
            cursor: 'pointer',
            fontSize: '2rem',
            color: 'white'
          }}
        >
          {isRecording ? '⏸' : '🎤'}
        </button>

        <button
          className="btn-primary"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.8rem',
            fontSize: '1rem'
          }}
        >
          📤 Share to WhatsApp
        </button>
      </div>
    </div>
  );
};

export default App;
