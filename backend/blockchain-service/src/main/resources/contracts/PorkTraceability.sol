// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.20;

contract PorkTraceability {
    struct Evidence {
        string bizType;
        string bizKey;
        string contentHash;
        string payload;
        uint256 timestamp;
        uint256 version;
    }

    address public owner;
    mapping(address => bool) public writers;
    mapping(string => Evidence[]) private evidenceHistory;

    event EvidenceStored(
        string indexed bizType,
        string indexed bizKey,
        string contentHash,
        uint256 version,
        uint256 timestamp
    );
    event WriterChanged(address indexed account, bool enabled);

    modifier onlyOwner() {
        require(msg.sender == owner, "owner only");
        _;
    }

    modifier onlyWriter() {
        require(writers[msg.sender], "writer only");
        _;
    }

    constructor() {
        owner = msg.sender;
        writers[msg.sender] = true;
    }

    function setWriter(address account, bool enabled) external onlyOwner {
        require(account != address(0), "invalid account");
        writers[account] = enabled;
        emit WriterChanged(account, enabled);
    }

    function storeEvidence(
        string calldata bizType,
        string calldata bizKey,
        string calldata contentHash,
        string calldata payload
    ) external onlyWriter returns (uint256 version) {
        require(bytes(bizType).length > 0, "bizType required");
        require(bytes(bizKey).length > 0, "bizKey required");
        require(bytes(contentHash).length == 64, "invalid SHA-256 hash");

        Evidence[] storage history = evidenceHistory[bizKey];
        if (history.length > 0) {
            Evidence storage latest = history[history.length - 1];
            if (keccak256(bytes(latest.contentHash)) == keccak256(bytes(contentHash))) {
                return latest.version;
            }
        }

        version = history.length + 1;
        history.push(Evidence(bizType, bizKey, contentHash, payload, block.timestamp, version));
        emit EvidenceStored(bizType, bizKey, contentHash, version, block.timestamp);
    }

    function latestEvidence(string calldata bizKey) external view returns (Evidence memory) {
        Evidence[] storage history = evidenceHistory[bizKey];
        require(history.length > 0, "evidence not found");
        return history[history.length - 1];
    }

    function evidenceAt(string calldata bizKey, uint256 version) external view returns (Evidence memory) {
        require(version > 0 && version <= evidenceHistory[bizKey].length, "invalid version");
        return evidenceHistory[bizKey][version - 1];
    }

    function versionCount(string calldata bizKey) external view returns (uint256) {
        return evidenceHistory[bizKey].length;
    }

    function verifyIntegrity(string calldata bizKey, string calldata hashToVerify)
        external view returns (bool matched, string memory onChainHash)
    {
        Evidence[] storage history = evidenceHistory[bizKey];
        if (history.length == 0) return (false, "");
        onChainHash = history[history.length - 1].contentHash;
        matched = keccak256(bytes(onChainHash)) == keccak256(bytes(hashToVerify));
    }
}
