/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *  *
 * Golden file test - simple task for regression testing.
 * This file is used to verify that IR and HDL generation produce consistent output.
 */

/**
 * Simple counter task for golden file testing.
 * Increments a counter on each cycle and outputs it.
 */
module Counter(input clock, input reset_n, output reg [7 : 0] count);


  /**
   * State variables
   */
  reg [7 : 0] counter;
  
  
  
  
  
  /**
   * Synchronous process
   */
  always @(negedge reset_n or posedge clock) begin // body of Counter
    if (~reset_n) begin
      counter <= 8'h0;
      count <= 8'b0;
    end else begin
      
      begin : FSM_Counter_a // line 19
        reg [8 : 0] orion;
      
        count <= counter;
        orion = (counter + 8'h1);
        counter <= orion[7 : 0];
      end
    end
  end

endmodule //Counter
