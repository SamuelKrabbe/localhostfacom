/** The app name is a shell session: `root@facom:~# curl localhost:<year>`. */
export interface WordmarkSegment {
  kind: 'prompt' | 'command' | 'host' | 'port' | 'path';
  text: string;
}

export function wordmarkSegments(path = ''): WordmarkSegment[] {
  const segments: WordmarkSegment[] = [
    { kind: 'prompt', text: 'root@facom:~# ' },
    { kind: 'command', text: 'curl ' },
    { kind: 'host', text: 'localhost' },
    { kind: 'port', text: `:${new Date().getFullYear()}` },
  ];
  if (path) {
    segments.push({ kind: 'path', text: path });
  }
  return segments;
}

export function wordmarkText(path = ''): string {
  return wordmarkSegments(path)
    .map((segment) => segment.text)
    .join('');
}
