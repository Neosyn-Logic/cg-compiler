/*
 * Copyright (c) 2012-2021, Neosyn
 * All rights reserved.
 * 
 * REDISTRIBUTION of this file in source and binary forms, with or without
 * modification, is NOT permitted in any way.
 *
 * The use of this file in source and binary forms, with or without
 * modification, is permitted if you have a valid commercial license of
 * Neosyn IDE.
 * If you do NOT have a valid license of Neosyn IDE: you are NOT allowed
 * to use this file.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE
 */

/**
 * Title   : FIFO read controller
 * Authors : Neosyn team <nicolas.siret@neosyn.io>
 */
module FIFO_Flag_Async
  #(parameter depth = 8)
  (
    input reset_n,
    input [depth - 1 : 0] rd_address,
    input [depth - 1 : 0] wr_address,
    output aFull,
    output aEmpty
  );

  wire dirSet_n;
  wire dirReset;
  reg  direction;
  
  assign dirSet_n = ~((wr_address[depth - 1] ^ rd_address[depth - 2]) & (~(wr_address[depth - 2] ^ rd_address[depth - 1])));
  assign dirReset = ~((wr_address[depth - 2] ^ rd_address[depth - 1]) & (~(wr_address[depth - 1] ^ rd_address[depth - 2])) | (~reset_n));
  assign aFull  =  direction & (wr_address == rd_address);
  assign aEmpty = ~direction & (wr_address == rd_address);
  
  always @(dirSet_n, dirReset)
    if (~dirReset)
      direction <= 1'b0;
    else if (~dirSet_n)
      direction <= 1'b1;
    
endmodule
