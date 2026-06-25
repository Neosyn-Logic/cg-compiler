/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *  *
 * Golden file test - simple task for regression testing.
 * This file is used to verify that IR and HDL generation produce consistent output.
 */

/**
 * Simple adder task for golden file testing.
 * Adds two inputs and produces the sum.
 */
module Adder(input clock, input reset_n, input [7 : 0] a, input [7 : 0] b, output reg [7 : 0] sum);


  /**
   * State variables
   */
  
  
  
  
  
  /**
   * Synchronous process
   */
  always @(negedge reset_n or posedge clock) begin // body of Adder
    if (~reset_n) begin
      sum <= 8'b0;
    end else begin
      
      begin : FSM_Adder_a // line 33
        reg [8 : 0] orion;
      
        orion = (a + b);
        sum <= orion[7 : 0];
      end
    end
  end

endmodule //Adder
