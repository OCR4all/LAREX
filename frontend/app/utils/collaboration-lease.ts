/** Compare decimal lease versions without converting them to floating-point numbers. */
export function compareLeaseEpochs(left: string, right: string): number {
  const leftEpoch = BigInt(left)
  const rightEpoch = BigInt(right)
  return leftEpoch < rightEpoch ? -1 : leftEpoch > rightEpoch ? 1 : 0
}
