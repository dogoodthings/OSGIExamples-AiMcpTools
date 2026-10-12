//*****************************************************************************
//
//                            Copyright (c) 2026
//                    DSC Software AG, Karlsruhe, Germany
//                            All rights reserved
//
//        The contents of this file is an unpublished work protected
//        under the copyright law of the Federal Republic of Germany
//
//        This software is proprietary to and embodies confidential
//        technology of DSC Software AG. Possession, use and copying
//        of the software and media is authorized only pursuant to a
//        valid written license from DSC Software AG. This copyright
//        statement must be visibly included in all copies.
//
//*****************************************************************************

package org.dogoodthings.ectr.companion.tools;

import java.util.Collection;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.dscsag.plm.spi.interfaces.ai.tools.ToolFactory;

/**
 * @author wt
 * @since 1.0.0
 */
@Component (service = ToolFactory.class)
public class ExampleMcpToolFactory implements ToolFactory
{
  @Reference ExampleMcpTool exampleMcpTool;
  @Reference MaterialNetWeightMcpTool materialNetWeightMcpTool;
  @Override
  public Collection<Object> tools()
  {
    return List.of(exampleMcpTool, materialNetWeightMcpTool);
  }

  @Override
  public String getName()
  {
    return "example osgi tool factory";
  }
}