import { useEffect, useRef } from "react";
import * as THREE from "three";
import type { ConsoleMode } from "../hooks/useJarvisApi";

interface AvatarSceneProps {
  mode: ConsoleMode;
  status: string;
}

export function AvatarScene({ mode, status }: AvatarSceneProps) {
  const mountRef = useRef<HTMLDivElement | null>(null);
  const modeRef = useRef<ConsoleMode>(mode);

  useEffect(() => {
    modeRef.current = mode;
  }, [mode]);

  useEffect(() => {
    const mount = mountRef.current;
    if (!mount) {
      return undefined;
    }

    const host = mount;
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100);
    camera.position.set(0, 0.25, 6);

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    host.append(renderer.domElement);

    const coreMaterial = new THREE.MeshStandardMaterial({
      color: 0x9bdcff,
      emissive: 0x174e6a,
      metalness: 0.38,
      roughness: 0.2,
    });
    const core = new THREE.Mesh(new THREE.IcosahedronGeometry(0.9, 3), coreMaterial);
    scene.add(core);

    const ringGroup = new THREE.Group();
    scene.add(ringGroup);
    [1.5, 2.05, 2.6].forEach((radius, index) => {
      const ring = new THREE.Mesh(
        new THREE.TorusGeometry(radius, 0.012, 12, 120),
        new THREE.MeshBasicMaterial({
          color: index === 1 ? 0x7bf0bd : 0x7ab8ff,
          transparent: true,
          opacity: 0.78,
        }),
      );
      ring.rotation.x = Math.PI / (2 + index);
      ring.rotation.y = index * 0.7;
      ringGroup.add(ring);
    });

    const particles = new THREE.Points(
      new THREE.BufferGeometry().setAttribute("position", new THREE.Float32BufferAttribute(makeParticlePositions(), 3)),
      new THREE.PointsMaterial({ color: 0xffffff, size: 0.018, transparent: true, opacity: 0.65 }),
    );
    scene.add(particles);

    scene.add(new THREE.AmbientLight(0xffffff, 1.8));
    const key = new THREE.PointLight(0x8ee6ff, 18, 10);
    key.position.set(3, 3, 4);
    scene.add(key);

    function resize() {
      const rect = host.getBoundingClientRect();
      const size = Math.max(280, Math.min(rect.width, rect.height || rect.width));
      renderer.setSize(size, size, false);
      camera.aspect = 1;
      camera.updateProjectionMatrix();
    }

    let animationFrame = 0;
    function animate() {
      const speed = speedForMode(modeRef.current);
      applyMaterialForMode(coreMaterial, modeRef.current);
      core.rotation.x += speed * 0.7;
      core.rotation.y += speed;
      ringGroup.rotation.z += speed;
      particles.rotation.y -= speed * 0.25;
      renderer.render(scene, camera);
      animationFrame = requestAnimationFrame(animate);
    }

    resize();
    window.addEventListener("resize", resize);
    animate();

    return () => {
      cancelAnimationFrame(animationFrame);
      window.removeEventListener("resize", resize);
      host.removeChild(renderer.domElement);
      renderer.dispose();
      core.geometry.dispose();
      coreMaterial.dispose();
      ringGroup.children.forEach((child) => {
        const mesh = child as THREE.Mesh<THREE.BufferGeometry, THREE.Material>;
        mesh.geometry.dispose();
        mesh.material.dispose();
      });
      particles.geometry.dispose();
      (particles.material as THREE.Material).dispose();
    };
  }, []);

  return (
    <section className="avatar-stage" aria-label="JARVIS avatar">
      <div ref={mountRef} className="avatar-canvas" aria-hidden="true" />
      <div className="avatar-readout">
        <p>Mode: {mode}</p>
        <p>{status}</p>
      </div>
    </section>
  );
}

function speedForMode(mode: ConsoleMode): number {
  if (mode === "thinking") {
    return 0.018;
  }
  if (mode === "guardian") {
    return 0.024;
  }
  return 0.008;
}

function applyMaterialForMode(material: THREE.MeshStandardMaterial, mode: ConsoleMode) {
  if (mode === "guardian") {
    material.color.setHex(0xffc15a);
    material.emissive.setHex(0x6b2f0f);
    return;
  }
  if (mode === "error") {
    material.color.setHex(0xff6b7c);
    material.emissive.setHex(0x5a1022);
    return;
  }
  if (mode === "success") {
    material.color.setHex(0x7bf0bd);
    material.emissive.setHex(0x124d35);
    return;
  }
  material.color.setHex(0x9bdcff);
  material.emissive.setHex(0x174e6a);
}

function makeParticlePositions(): number[] {
  const positions: number[] = [];
  for (let index = 0; index < 460; index += 1) {
    const radius = 2.3 + Math.random() * 1.8;
    const theta = Math.random() * Math.PI * 2;
    const phi = Math.acos(2 * Math.random() - 1);
    positions.push(
      radius * Math.sin(phi) * Math.cos(theta),
      radius * Math.sin(phi) * Math.sin(theta),
      radius * Math.cos(phi),
    );
  }
  return positions;
}
