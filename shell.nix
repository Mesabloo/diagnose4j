{ pkgs ? import <nixpkgs> { } }:

pkgs.mkShell {
  name = "diagnose4j-shell";
  version = "0.0.1";

  buildInputs = with pkgs; [
    jetbrains.idea-community
    gradle
  ];
}


